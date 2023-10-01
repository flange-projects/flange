/*
 * Copyright © 2023 GlobalMentor, Inc. <https://www.globalmentor.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.flange.build.cloud;

import static com.globalmentor.collections.iterables.Iterables.*;
import static com.globalmentor.io.ClassResources.*;
import static com.globalmentor.java.Conditions.*;
import static com.globalmentor.java.Objects.*;
import static java.lang.System.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.*;
import static java.util.stream.Collectors.*;
import static javax.lang.model.util.ElementFilter.*;
import static javax.tools.Diagnostic.Kind.*;
import static javax.tools.StandardLocation.*;
import static org.zalando.fauxpas.FauxPas.*;

import java.io.*;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.annotation.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.FileObject;

import com.globalmentor.model.ConfiguredStateException;
import com.squareup.javapoet.*;

import dev.flange.cloud.*;

/**
 * Annotation processor for FaaS.
 * @author Garret Wilson
 */
public class FaasProcessor extends AbstractProcessor {

	/** The fully qualified class names representing annotation type supported by this processor. */
	public static final Set<String> SUPPORTED_ANNOTATION_TYPES = Stream.of(FaasService.class, ServiceConsumer.class).map(Class::getName)
			.collect(toUnmodifiableSet());

	/**
	 * The resource containing the AWS Lambda assembly descriptor.
	 * @see <a href="https://maven.apache.org/plugins/maven-assembly-plugin/">Apache Maven Assembly Plugin</a>.
	 */
	private static final String RESOURCE_ASSEMBLY_DESCRIPTOR_AWS_LAMBDA = "aws/assembly-descriptor-aws-lambda.xml";

	/**
	 * The resource containing the Log4j config file.
	 * @implNote The assembly descriptor for AWS Lambda will rename the file as needed for including in the AWS Lambda ZIP file.
	 * @see <a href="https://logging.apache.org/log4j/2.x/manual/configuration.html">Log4j Configuration</a>.
	 */
	private static final String RESOURCE_LOG4J_CONFIG_AWS_LAMBDA = "aws/log4j2-aws-lambda.xml";

	/**
	 * The resource containing the introduction of the SAM template for the project.
	 * @see <a href="https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/sam-specification-template-anatomy.html">AWS SAM template
	 *      anatomy</a>.
	 */
	private static final String RESOURCE_SAM_INTRO = "aws/sam-intro.yaml";

	/**
	 * {@inheritDoc}
	 * @implSpec This processor supports {@link #SUPPORTED_ANNOTATION_TYPES}.
	 */
	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return SUPPORTED_ANNOTATION_TYPES;
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This processor supports the latest supported source version.
	 */
	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	private static final ClassName ABSTRACT_FAAS_API_LAMBDA_STUB_CLASS_NAME = ClassName.get("dev.flange.cloud.aws", "AbstractFaasApiLambdaStub"); //TODO consider importing AWS classes
	private static final ClassName FAAS_SERVICE_LAMBDA_HANDLER_CLASS_NAME = ClassName.get("dev.flange.cloud.aws", "FaasServiceLambdaHandler");

	private static final String LAMBDA_STUB_CLASS_NAME_SUFFIX = "_FlangeLambdaStub"; //TODO rename to include "Aws"
	private static final String LAMBDA_HANDLER_CLASS_NAME_SUFFIX = "_FlangeLambdaHandler"; //TODO rename to include "Aws"

	private final Set<ClassName> cloudFunctionServiceImplClassNames = new HashSet<>();
	private final Set<ClassName> awsFunctionServiceSkeletonClassNames = new HashSet<>();
	private final Map<TypeElement, Set<TypeElement>> consumerTypeElementsByCloudFunctionApiTypeElement = new HashMap<>(); //TODO tidy

	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnvironment) {
		try {
			//@CloudFunctionService
			{
				final Set<? extends Element> annotatedElements = roundEnvironment.getElementsAnnotatedWith(FaasService.class);
				final Set<TypeElement> typeElements = typesIn(annotatedElements);
				//TODO raise error if there are non-type elements
				for(final TypeElement typeElement : typeElements) {
					final ClassName serviceImplClassName = ClassName.get(typeElement);
					final ClassName awsLambdaServiceHandlerClassName = generateAwsFunctionServiceSkeletonClass(typeElement);
					cloudFunctionServiceImplClassNames.add(serviceImplClassName);
					awsFunctionServiceSkeletonClassNames.add(awsLambdaServiceHandlerClassName);
					generateFaasServiceLambdaAssemblyDescriptor(typeElement);
				}
			}
			//@ServiceClient
			{
				final Set<? extends Element> annotatedElements = roundEnvironment.getElementsAnnotatedWith(ServiceConsumer.class);
				final Set<TypeElement> typeElements = typesIn(annotatedElements);
				//TODO raise error if there are non-type elements
				for(final TypeElement typeElement : typeElements) {
					final AnnotationMirror serviceConsumerAnnotationMirror = findAnnotationMirror(typeElement, ServiceConsumer.class).orElseThrow(AssertionError::new); //we already know it has the annotation
					@SuppressWarnings("unchecked")
					final List<? extends AnnotationValue> serviceClassAnnotationValues = findValueElementValue(serviceConsumerAnnotationMirror)
							.map(AnnotationValue::getValue).flatMap(asInstance(List.class)).orElseThrow(() -> new IllegalStateException(
									"The `@%s` annotation of the element `%s` has no array value.".formatted(ServiceConsumer.class.getSimpleName(), typeElement)));
					serviceClassAnnotationValues.stream().map(AnnotationValue::getValue).map(Object::toString) //consumed service class names
							.map(className -> findTypeElement(processingEnv.getElementUtils(), className) //consumed service type elements
									.orElseThrow(() -> new IllegalStateException("Unable to find a single type element for service class `%s`.".formatted(className))))
							.forEach(serviceTypeElement -> {
								if(findAnnotationMirror(serviceTypeElement, FaasApi.class).isPresent()) {
									consumerTypeElementsByCloudFunctionApiTypeElement.computeIfAbsent(serviceTypeElement, __ -> new LinkedHashSet<>()).add(typeElement);
								}
							});
				}
			}
			if(roundEnvironment.processingOver()) {
				if(!cloudFunctionServiceImplClassNames.isEmpty()) {
					generateFlangeDependenciesList(cloudFunctionServiceImplClassNames);
				}
				if(!awsFunctionServiceSkeletonClassNames.isEmpty()) {
					generateFaasServiceSamTemplate(awsFunctionServiceSkeletonClassNames);
					generateFaasServiceLambdaLog4jConfigFile();
				}
				//TODO consider generating the first implementation during rounds if no others have been generated (see https://stackoverflow.com/q/27886169 for warning), and maybe checking current dependency list, if any, to support incremental compilation
				consumerTypeElementsByCloudFunctionApiTypeElement.forEach(throwingBiConsumer((serviceApiTypeElement, serviceConsumerTypeElements) -> {
					generateAwsFunctionStubClass(serviceApiTypeElement, serviceConsumerTypeElements);

				}));
			}
		} catch(final IOException ioException) {
			processingEnv.getMessager().printMessage(ERROR, ioException.getMessage()); //TODO improve
		}

		return true;
	}

	/**
	 * Generates the AWS Lambda stub class for a FaaS API.
	 * @implSpec This implementation places the generated stub class in the same package of the first encountered consumer.
	 * @implNote A future implementation may use more reasoning in determining the package name, such as choosing the highest package in the imputed package tree.
	 * @param serviceApiTypeElement The element representing the API of the FaaS service to be invoked by the stub.
	 * @param serviceConsumerTypeElements The elements representing the known consumers of the service
	 * @throws IOException if there is an I/O error writing the class.
	 * @return The class name of the generated stub class.
	 */
	protected ClassName generateAwsFunctionStubClass(@Nonnull final TypeElement serviceApiTypeElement, @Nonnull Set<TypeElement> serviceConsumerTypeElements)
			throws IOException {
		final ClassName serviceApiClassName = ClassName.get(serviceApiTypeElement);
		//TODO this probably won't work correctly with incremental compiling, so find a consistent way to determine a good package name for the stub, or take care not to replace a previous one during incremental compilation
		final String lambdaStubPackageName = findFirst(serviceConsumerTypeElements).map(ClassName::get).map(ClassName::packageName)
				.orElseThrow(() -> new IllegalArgumentException("No service API consumers given."));
		final ClassName awsLambdaStubClassName = ClassName.get(lambdaStubPackageName, serviceApiClassName.simpleName() + LAMBDA_STUB_CLASS_NAME_SUFFIX);
		//`public final class ServiceApi_FlangeLambdaStub implements ServiceApi …`
		final TypeSpec.Builder lambdaStubClassSpecBuilder = TypeSpec.classBuilder(awsLambdaStubClassName).addOriginatingElement(serviceApiTypeElement) //
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL).superclass(ABSTRACT_FAAS_API_LAMBDA_STUB_CLASS_NAME).addSuperinterface(serviceApiClassName) //
				.addJavadoc("AWS Lambda stub for {@link $T}.", serviceApiClassName);
		final DeclaredType completableFutureUnboundedWildcardType = findUnboundedWildcardType(processingEnv, CompletableFuture.class)
				.orElseThrow(IllegalStateException::new);
		methodsIn(serviceApiTypeElement.getEnclosedElements()).forEach(methodElement -> {
			if(methodElement.isDefault()) { //don't override default method implementations
				return;
			}
			final TypeMirror returnTypeMirror = methodElement.getReturnType();
			if(processingEnv.getTypeUtils().isAssignable(returnTypeMirror, completableFutureUnboundedWildcardType)) { //TODO add support for `Future<?>`
				MethodSpec.Builder methodSpecBuilder = MethodSpec.overriding(methodElement);
				methodSpecBuilder.addStatement("throw new $T()", UnsupportedOperationException.class); //TODO finish
				lambdaStubClassSpecBuilder.addMethod(methodSpecBuilder.build());
			} //TODO else produce an error indicating that the return type is unsupported
		});
		final TypeSpec lambdaStubClassSpec = lambdaStubClassSpecBuilder.build();
		final JavaFile lambdaHandlerClassJavaFile = JavaFile.builder(lambdaStubPackageName, lambdaStubClassSpec) //
				//TODO consolidate intro comments
				.addFileComment("""
						File generated by [Flange](https://flange.dev/), a tool by
						[GlobalMentor, Inc.](https://www.globalmentor.com/).

						_Normally this file should not be edited manually._""") //
				.skipJavaLangImports(true).build();
		lambdaHandlerClassJavaFile.writeTo(processingEnv.getFiler());
		return awsLambdaStubClassName;
	}

	/**
	 * Generates the AWS Lambda skeleton class for a FaaS service implementation.
	 * @param serviceImplTypeElement The element representing the implementation of the FaaS service to be invoked by the skeleton.
	 * @throws IOException if there is an I/O error writing the class.
	 * @return The class name of the generated skeleton class.
	 */
	protected ClassName generateAwsFunctionServiceSkeletonClass(@Nonnull final TypeElement serviceImplTypeElement) throws IOException {
		final ClassName serviceImplClassName = ClassName.get(serviceImplTypeElement);
		final ClassName awsLambdaHandlerClassName = ClassName.get(serviceImplClassName.packageName(),
				serviceImplClassName.simpleName() + LAMBDA_HANDLER_CLASS_NAME_SUFFIX);
		final TypeName awsLambdaHandlerSuperClassName = ParameterizedTypeName.get(FAAS_SERVICE_LAMBDA_HANDLER_CLASS_NAME, serviceImplClassName);
		//TODO look at the implemented interfaces to determine the API name to use, not the implementation
		//`public final class MyServiceImpl_FlangeLambdaHandler …`
		final TypeSpec awsLambdaHandlerClassSpec = TypeSpec.classBuilder(awsLambdaHandlerClassName).addOriginatingElement(serviceImplTypeElement) //
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL).superclass(awsLambdaHandlerSuperClassName) //
				.addJavadoc("AWS Lambda handler skeleton for {@link $T}.", serviceImplClassName) //
				.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC) //constructor
						.addJavadoc("Constructor.") //
						.addStatement("super($T.class)", serviceImplClassName).build())
				.build();
		final JavaFile awsLambdaHandlerClassJavaFile = JavaFile.builder(serviceImplClassName.packageName(), awsLambdaHandlerClassSpec) //
				.addFileComment("""
						File generated by [Flange](https://flange.dev/), a tool by
						[GlobalMentor, Inc.](https://www.globalmentor.com/).

						_Normally this file should not be edited manually._""") //
				.skipJavaLangImports(true).build();
		awsLambdaHandlerClassJavaFile.writeTo(processingEnv.getFiler());
		return awsLambdaHandlerClassName;
	}

	/**
	 * Generates the AWS Lambda assembly descriptor for a FaaS service implementation.
	 * @param faasServiceImplTypeElement The element representing the implementation of the FaaS service for which an assembly will be created.
	 * @throws IOException if there is an I/O error writing the assembly descriptor.
	 */
	protected void generateFaasServiceLambdaAssemblyDescriptor(@Nonnull final TypeElement faasServiceImplTypeElement) throws IOException {
		final String lambdaHandlerClassSimpleName = faasServiceImplTypeElement.getSimpleName() + LAMBDA_HANDLER_CLASS_NAME_SUFFIX;
		final String assemblyDescriptorFilename = "assembly-" + lambdaHandlerClassSimpleName + "-aws-lambda.xml"; //TODO use constant
		try (
				final InputStream inputStream = findResourceAsStream(getClass(), RESOURCE_ASSEMBLY_DESCRIPTOR_AWS_LAMBDA)
						.orElseThrow(() -> new ConfiguredStateException("Missing class resource `%s`.".formatted(RESOURCE_ASSEMBLY_DESCRIPTOR_AWS_LAMBDA)));
				final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
			final FileObject outputFileObject = processingEnv.getFiler().createResource(SOURCE_OUTPUT, "", assemblyDescriptorFilename, faasServiceImplTypeElement);
			try (final Writer writer = new OutputStreamWriter(new BufferedOutputStream(outputFileObject.openOutputStream()), UTF_8)) {
				String line; //write individual lines to produce line endings consistent with system; note that this will also normalize the file to end with a line ending
				while((line = bufferedReader.readLine()) != null) {
					writer.write(line);
					writer.write(lineSeparator());
				}
			}
		}
	}

	/**
	 * Generates the Flange dependencies list indicating backing service implementations used by the AWS Lambda handlers.
	 * @param faasServiceLambdaImplClassNames The class names of the backing FaaS service implementations.
	 * @throws IOException if there is an I/O error writing the dependencies list.
	 */
	protected void generateFlangeDependenciesList(@Nonnull final Set<ClassName> faasServiceLambdaImplClassNames) throws IOException {
		final String dependenciesListFilename = "flange-dependencies.lst"; //TODO reference constant from Flange; change name to `…_cloud-aws` and rename when assembling (or merge in future version)
		final FileObject outputFileObject = processingEnv.getFiler().createResource(SOURCE_OUTPUT, "", dependenciesListFilename);
		try (final Writer writer = new OutputStreamWriter(new BufferedOutputStream(outputFileObject.openOutputStream()), UTF_8)) {
			for(final ClassName faasServiceLambdaHandlerClassName : faasServiceLambdaImplClassNames) {
				writer.write(faasServiceLambdaHandlerClassName.canonicalName());
				writer.write(lineSeparator());
			}
		}
	}

	/**
	 * Generates the Log4j configuration file for AWS Lambda.
	 * @implSpec The file will be generated as <code>log4j2-aws-lambda.xml</code> in the root of the generated sources directory. Only one file will be needed for
	 *           as many AWS Lambda instances are configured for the project.
	 * @throws IOException if there is an I/O error writing the Log4j config file.
	 */
	protected void generateFaasServiceLambdaLog4jConfigFile() throws IOException {
		final String log4jConfigFilename = "log4j2-aws-lambda.xml"; //TODO use constant, or use defined resource name
		try (
				final InputStream inputStream = findResourceAsStream(getClass(), RESOURCE_LOG4J_CONFIG_AWS_LAMBDA)
						.orElseThrow(() -> new ConfiguredStateException("Missing class resource `%s`.".formatted(RESOURCE_LOG4J_CONFIG_AWS_LAMBDA)));
				final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
			final FileObject outputFileObject = processingEnv.getFiler().createResource(SOURCE_OUTPUT, "", log4jConfigFilename);
			try (final Writer writer = new OutputStreamWriter(new BufferedOutputStream(outputFileObject.openOutputStream()), UTF_8)) {
				String line; //write individual lines to produce line endings consistent with system; note that this will also normalize the file to end with a line ending
				while((line = bufferedReader.readLine()) != null) {
					writer.write(line);
					writer.write(lineSeparator());
				}
			}
		}
	}

	/**
	 * Generates the SAM template for deploying a FaaS service implementation.
	 * @param faasServiceLambdaHandlerClassNames The class names of the generated AWS Lambda handler skeleton classes.
	 * @throws IOException if there is an I/O error writing the SAM template.
	 */
	protected void generateFaasServiceSamTemplate(@Nonnull final Set<ClassName> faasServiceLambdaHandlerClassNames) throws IOException {
		final String samFilename = "sam.yaml"; //TODO use constant
		try (
				final InputStream inputStream = findResourceAsStream(getClass(), RESOURCE_SAM_INTRO)
						.orElseThrow(() -> new ConfiguredStateException("Missing class resource `%s`.".formatted(RESOURCE_SAM_INTRO)));
				final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
			final FileObject outputFileObject = processingEnv.getFiler().createResource(SOURCE_OUTPUT, "", samFilename);
			try (final Writer writer = new OutputStreamWriter(new BufferedOutputStream(outputFileObject.openOutputStream()), UTF_8)) {
				String line; //write individual lines to produce line endings consistent with system; note that this will also ensure a trailing newline
				while((line = bufferedReader.readLine()) != null) {
					writer.write(line);
					writer.write(lineSeparator());
				}
				writer.write("%n".formatted()); //TODO eventually parse and interpolate the original YAML file
				writer.write("Resources:%n%n".formatted());
				for(final ClassName faasServiceLambdaHandlerClassName : faasServiceLambdaHandlerClassNames) {
					writer.write("  %s:%n".formatted(faasServiceLambdaHandlerClassName.simpleName().replace("_", ""))); //TODO refactor using logical ID sanitizing method
					writer.write("    Type: AWS::Serverless::Function%n".formatted());
					writer.write("    Properties:%n".formatted());
					writer.write("      FunctionName: !Sub \"flange-${Env}-%s\"%n".formatted(faasServiceLambdaHandlerClassName.simpleName()));
					writer.write("      CodeUri:%n".formatted());
					writer.write("        Bucket:%n".formatted());
					writer.write("          Fn::ImportValue:%n".formatted());
					writer.write("            !Sub \"flange-${Env}:StagingBucketName\"%n".formatted());
					writer.write("        Key: !Sub \"%s-aws-lambda.zip\"%n".formatted(faasServiceLambdaHandlerClassName.simpleName())); //TODO later interpolate version 
					writer.write("      Handler: %s::%s%n".formatted(faasServiceLambdaHandlerClassName.canonicalName(), "handleRequest")); //TODO use constant
					//TODO add environment variable for active profiles
				}
			}
		}
	}

	//## Elements

	/**
	 * Retrieves a single all the annotation mirrors from a type element for annotations of a particular type.
	 * @implSpec This implementation does not check for repeated annotations.
	 * @param typeElement The type element representing the type potentially annotated with the specified annotation.
	 * @param annotationClass The type of annotation to find.
	 * @return The mirrors for the annotation annotating the indicated type, if any.
	 */
	static Optional<? extends AnnotationMirror> findAnnotationMirror(@Nonnull final TypeElement typeElement,
			@Nonnull final Class<? extends Annotation> annotationClass) {
		return annotationMirrors(typeElement, annotationClass).findAny();
	}

	/**
	 * Retrieves all the annotation mirrors from a type element for annotations of a particular type.
	 * @param typeElement The type element representing the type potentially annotated with the specified annotation.
	 * @param annotationClass The type of annotation to find.
	 * @return The mirrors for the annotation(s) annotating the indicated type, if any.
	 */
	static Stream<? extends AnnotationMirror> annotationMirrors(@Nonnull final TypeElement typeElement,
			@Nonnull final Class<? extends Annotation> annotationClass) {
		final String canonicalName = annotationClass.getCanonicalName();
		checkArgument(canonicalName != null, "Annotation class `%s` has no canonical name.", annotationClass.getName()); //check for completeness; not realistically possible: an annotation cannot be defined as an anonymous inner class
		return typeElement.getAnnotationMirrors().stream().filter(annotationMirror -> {
			final Element annotationElement = annotationMirror.getAnnotationType().asElement();
			assert annotationElement instanceof TypeElement : "An annotation mirror type's element should always be a `TypeElement`.";
			return ((TypeElement)annotationElement).getQualifiedName().contentEquals(canonicalName);
		});
	}

	//## Mirrors

	/**
	 * Finds the annotation value mapped to the <code>value</code> element from an annotation mirror.
	 * @apiNote The <code>value</code> element is the special element which allows the element value to be left out in the source file.
	 * @implSpec This implementation delegates to {@link #findElementValueBySimpleName(AnnotationMirror, CharSequence)}.
	 * @param annotationMirror The annotation mirror in which to look up an element value.
	 * @return The annotation value if found.
	 */
	static Optional<? extends AnnotationValue> findValueElementValue(@Nonnull AnnotationMirror annotationMirror) {
		return findElementValueBySimpleName(annotationMirror, "value"); //TODO use constant
	}

	/**
	 * Finds the annotation value mapped to the executable element with the given simple name from an annotation mirror.
	 * @implSpec This implementation calls {@link AnnotationMirror#getElementValues()} and then delegates to
	 *           {@link #findElementValueBySimpleName(Map, CharSequence)}.
	 * @param annotationMirror The annotation mirror in which to look up an element value.
	 * @param simpleName The simple name (i.e. property name) of the value to retrieve.
	 * @return The annotation value if found.
	 */
	static Optional<? extends AnnotationValue> findElementValueBySimpleName(@Nonnull AnnotationMirror annotationMirror, @Nonnull final CharSequence simpleName) {
		return findElementValueBySimpleName(annotationMirror.getElementValues(), simpleName);
	}

	/**
	 * Finds the annotation value mapped to the executable element with the given simple name.
	 * @apiNote This is useful for finding a value of an {@link AnnotationMirror} from the map returned by {@link AnnotationMirror#getElementValues()}.
	 * @param elementValues The map of element values associated with their executable elements (e.g. accessor methods of an annotation mirror).
	 * @param simpleName The simple name (i.e. element name) of the value to retrieve.
	 * @return The annotation value if found.
	 * @see <a href="https://area-51.blog/2009/02/13/getting-class-values-from-annotations-in-an-annotationprocessor/">Getting Class values from Annotations in an
	 *      AnnotationProcessor</a>
	 */
	static Optional<? extends AnnotationValue> findElementValueBySimpleName(
			@Nonnull final Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues, @Nonnull final CharSequence simpleName) {
		requireNonNull(simpleName);
		return elementValues.entrySet().stream().filter(entry -> entry.getKey().getSimpleName().contentEquals(simpleName)).findAny().map(Map.Entry::getValue);
	}

	//## Types 

	//## ProcessingEnvironment

	//### Elements Utilities

	/**
	 * Finds a returns a type element from a class if the type element is uniquely determinable in the environment.
	 * @implSpec This implementation delegates to {@link #findTypeElement(Elements, CharSequence)}.
	 * @param elements The element utilities.
	 * @param clazz The class for which a type element is to be found.
	 * @return The type element for the class, which will not be present if no type element can be uniquely determined.
	 * @see ProcessingEnvironment#getElementUtils()
	 * @see Class#getCanonicalName()
	 */
	static Optional<TypeElement> findTypeElement(@Nonnull final Elements elements, @Nonnull final Class<?> clazz) { //TODO create module-related variations as well
		return findTypeElement(elements, clazz.getCanonicalName());
	}

	/**
	 * Finds a returns a type element given its canonical name if the type element is uniquely determinable in the environment.
	 * @apiNote This method functions identically to {@link Elements#getTypeElement(CharSequence)} except that it returns an {@link Optional} and never
	 *          <code>null</code>.
	 * @implSpec This implementation delegates to {@link Elements#getTypeElement(CharSequence)}.
	 * @param elements The element utilities.
	 * @param canonicalName The canonical name of the type element to return.
	 * @return The named type element, which will not be present if no type element can be uniquely determined.
	 * @see ProcessingEnvironment#getElementUtils()
	 */
	static Optional<TypeElement> findTypeElement(@Nonnull final Elements elements, @Nonnull final CharSequence canonicalName) { //TODO create module-related variations as well
		return Optional.ofNullable(elements.getTypeElement(canonicalName));
	}

	/**
	 * Finds a returns a type corresponding to a class type element and actual type arguments, if the type element is uniquely determinable in the environment.
	 * @implSpec This implementation delegates to {@link #findTypeElement(Elements, Class)} followed by {@link Types#getDeclaredType(TypeElement, TypeMirror...)}.
	 * @param elements The element utilities.
	 * @param types The type utilities.
	 * @param clazz The class for which a type element is to be found.
	 * @param typeArgs The actual type arguments.
	 * @return The type element for the class, which will not be present if no type element can be uniquely determined.
	 * @see ProcessingEnvironment#getElementUtils()
	 * @see Class#getCanonicalName()
	 * @see Elements#getTypeElement(CharSequence)
	 * @see Types#getDeclaredType(TypeElement, TypeMirror...)
	 */
	static Optional<DeclaredType> findTypeElement(@Nonnull final Elements elements, @Nonnull final Types types, @Nonnull final Class<?> clazz,
			@Nonnull final TypeMirror... typeArgs) {
		return findTypeElement(elements, clazz).map(typeElement -> types.getDeclaredType(typeElement, typeArgs));
	}

	/**
	 * Finds a returns a type corresponding to a class type element and actual type arguments, if the type element is uniquely determinable in the environment.
	 * @implSpec This implementation delegates to {@link #findTypeElement(Elements, Types, Class, TypeMirror...)}.
	 * @param processingEnvironment The processing environment.
	 * @param clazz The class for which a type element is to be found.
	 * @param typeArgs The actual type arguments.
	 * @return The type element for the class, which will not be present if no type element can be uniquely determined.
	 * @see ProcessingEnvironment#getElementUtils()
	 * @see Class#getCanonicalName()
	 * @see Elements#getTypeElement(CharSequence)
	 * @see Types#getDeclaredType(TypeElement, TypeMirror...)
	 * @see ProcessingEnvironment#getElementUtils()
	 * @see ProcessingEnvironment#getTypeUtils()
	 */
	static Optional<DeclaredType> findTypeElement(@Nonnull ProcessingEnvironment processingEnvironment, @Nonnull final Class<?> clazz,
			@Nonnull final TypeMirror... typeArgs) {
		return findTypeElement(processingEnvironment.getElementUtils(), processingEnvironment.getTypeUtils(), clazz, typeArgs);
	}

	/**
	 * Finds a returns a type corresponding to a class type element with an unbounded wildcard type parameter, if the type element is uniquely determinable in the
	 * environment.
	 * @implSpec This implementation delegates to {@link #findTypeElement(Elements, Types, Class, TypeMirror...)} using the result of
	 *           #getUnboundedWildcardType(Types).
	 * @param elements The element utilities.
	 * @param types The type utilities.
	 * @param clazz The class for which a type element is to be found.
	 * @return The type element for the class, which will not be present if no type element can be uniquely determined.
	 * @see ProcessingEnvironment#getElementUtils()
	 * @see Class#getCanonicalName()
	 * @see Elements#getTypeElement(CharSequence)
	 * @see Types#getDeclaredType(TypeElement, TypeMirror...)
	 * @see #getUnboundedWildcardType(Types)
	 */
	static Optional<DeclaredType> findUnboundedWildcardType(@Nonnull final Elements elements, @Nonnull final Types types, @Nonnull final Class<?> clazz) {
		return findTypeElement(elements, types, clazz, getUnboundedWildcardType(types));
	}

	/**
	 * Finds a returns a type corresponding to a class type element with an unbounded wildcard type parameter, if the type element is uniquely determinable in the
	 * environment.
	 * @implSpec This implementation delegates to {@link #findUnboundedWildcardType(Elements, Types, Class)}.
	 * @param processingEnvironment The processing environment.
	 * @param clazz The class for which a type element is to be found.
	 * @return The type element for the class, which will not be present if no type element can be uniquely determined.
	 * @see ProcessingEnvironment#getElementUtils()
	 * @see Class#getCanonicalName()
	 * @see Elements#getTypeElement(CharSequence)
	 * @see Types#getDeclaredType(TypeElement, TypeMirror...)
	 * @see ProcessingEnvironment#getElementUtils()
	 * @see ProcessingEnvironment#getTypeUtils()
	 */
	static Optional<DeclaredType> findUnboundedWildcardType(@Nonnull ProcessingEnvironment processingEnvironment, @Nonnull final Class<?> clazz) {
		return findUnboundedWildcardType(processingEnvironment.getElementUtils(), processingEnvironment.getTypeUtils(), clazz);
	}

	//### Types Utilities

	/**
	 * Returns a new unbounded wildcard type ({@code <?>}).
	 * @implSpec This implementation delegates to {@link Types#getWildcardType(TypeMirror, TypeMirror)}, passing <code>null</code> for both bounds.
	 * @return The new unbounded wildcard type.
	 */
	static WildcardType getUnboundedWildcardType(@Nonnull final Types types) {
		return types.getWildcardType(null, null);
	}

}
