/*
 * Copyright © 2023 GlobalMentor, Inc. <https://www.globalmentor.com/>
// *
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
import static com.globalmentor.util.stream.Streams.*;
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
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.FileObject;

import com.fasterxml.classmate.GenericType;
import com.globalmentor.model.ConfiguredStateException;
import com.squareup.javapoet.*;

import dev.flange.cloud.*;
import dev.flange.cloud.aws.*;

/**
 * Annotation processor for Flange Cloud.
 * @author Garret Wilson
 */
public class FlangeCloudProcessor extends AbstractProcessor {

	/** The fully qualified class names representing annotation type supported by this processor. */
	public static final Set<String> SUPPORTED_ANNOTATION_TYPES = Stream.of(CloudFunctionService.class, ServiceConsumer.class).map(Class::getName)
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

	private static final ClassName ABSTRACT_AWS_CLOUD_FUNCTION_API_STUB_CLASS_NAME = ClassName.get(AbstractAwsCloudFunctionApiStub.class);
	private static final ClassName AWS_CLOUD_FUNCTION_SERVICE_HANDLER_CLASS_NAME = ClassName.get(AwsCloudFunctionServiceHandler.class);

	private static final String AWS_CLOUD_FUNCTION_STUB_CLASS_NAME_SUFFIX = "_FlangeAwsLambdaStub";
	private static final String AWS_CLOUD_FUNCTION_SKELETON_CLASS_NAME_SUFFIX = "_FlangeAwsLambdaSkeleton";

	private static final String DEPENDENCIES_LIST_FILENAME = "flange-dependencies.lst"; //TODO reference constant from Flange
	private static final String DEPENDENCIES_LIST_PLATFORM_AWS_FILENAME = "flange-dependencies_platform-aws.lst"; //TODO reference constant from Flange

	private final Set<ClassName> cloudFunctionServiceImplClassNames = new HashSet<>();
	private final Map<ClassName, Map.Entry<ClassName, ClassName>> awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames = new HashMap<>();
	private final Map<TypeElement, Set<TypeElement>> consumerTypeElementsByCloudFunctionApiTypeElement = new HashMap<>(); //TODO tidy

	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnvironment) {
		try {
			//@CloudFunctionService
			{
				final Set<? extends Element> annotatedElements = roundEnvironment.getElementsAnnotatedWith(CloudFunctionService.class); //TODO create utility to get types and ensure there are no non-types
				final Set<TypeElement> serviceImplTypeElements = typesIn(annotatedElements);
				//TODO raise error if there are non-type elements
				for(final TypeElement serviceImplTypeElement : serviceImplTypeElements) {

					final Optional<DeclaredType> foundCloudFunctionApiAnnotatedInterfaceType = interfacesAnnotatedWith(processingEnv.getElementUtils(),
							processingEnv.getTypeUtils(), serviceImplTypeElement, CloudFunctionApi.class).reduce(toFindOnly()); //TODO use improved reduction from JAVA-344 to provide an error if there are multiple interfaces found
					if(!foundCloudFunctionApiAnnotatedInterfaceType.isPresent()) {
						processingEnv.getMessager().printMessage(WARNING,
								"Service `%s` implements no interfaces annotated with `@%s`; generated cloud function may not be accessible from other system components."
										.formatted(serviceImplTypeElement, CloudFunctionApi.class.getSimpleName()));
					}
					final ClassName serviceImplClassName = ClassName.get(serviceImplTypeElement);
					cloudFunctionServiceImplClassNames.add(serviceImplClassName);
					final ClassName serviceApiClassName = foundCloudFunctionApiAnnotatedInterfaceType.map(DeclaredType::asElement).flatMap(asInstance(TypeElement.class))
							.map(ClassName::get).orElse(serviceImplClassName); //if we can't find an API interface, use the service class name itself, although other components can't find it
					final ClassName awsLambdaServiceHandlerClassName = generateAwsCloudFunctionServiceSkeletonClass(serviceImplTypeElement);
					awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames.put(serviceApiClassName,
							Map.entry(serviceImplClassName, awsLambdaServiceHandlerClassName));
					generateCloudFunctionServiceAwsLambdaAssemblyDescriptor(serviceImplTypeElement);
				}
			}
			//@ServiceClient
			{
				final Set<? extends Element> annotatedElements = roundEnvironment.getElementsAnnotatedWith(ServiceConsumer.class);
				final Set<TypeElement> serviceTypeClientElements = typesIn(annotatedElements);
				//TODO raise error if there are non-type elements
				for(final TypeElement serviceClientTypeElement : serviceTypeClientElements) {
					final AnnotationMirror serviceConsumerAnnotationMirror = findAnnotationMirror(serviceClientTypeElement, ServiceConsumer.class)
							.orElseThrow(AssertionError::new); //we already know it has the annotation
					@SuppressWarnings("unchecked")
					final List<? extends AnnotationValue> serviceClassAnnotationValues = findValueElementValue(serviceConsumerAnnotationMirror)
							.map(AnnotationValue::getValue).flatMap(asInstance(List.class)).orElseThrow(() -> new IllegalStateException(
									"The `@%s` annotation of the element `%s` has no array value.".formatted(ServiceConsumer.class.getSimpleName(), serviceClientTypeElement)));
					serviceClassAnnotationValues.stream().map(AnnotationValue::getValue).map(Object::toString) //consumed service class names
							.map(className -> findTypeElement(processingEnv.getElementUtils(), className) //consumed service type elements
									.orElseThrow(() -> new IllegalStateException("Unable to find a single type element for service class `%s`.".formatted(className))))
							.forEach(serviceTypeElement -> {
								if(findAnnotationMirror(serviceTypeElement, CloudFunctionApi.class).isPresent()) {
									consumerTypeElementsByCloudFunctionApiTypeElement.computeIfAbsent(serviceTypeElement, __ -> new LinkedHashSet<>())
											.add(serviceClientTypeElement);
								}
							});
				}
			}
			if(roundEnvironment.processingOver()) {
				if(!cloudFunctionServiceImplClassNames.isEmpty()) {
					generateFlangeDependenciesList(DEPENDENCIES_LIST_FILENAME, cloudFunctionServiceImplClassNames);
				}
				if(!awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames.isEmpty()) {
					generateAwsSamTemplate(awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames);
					generateAwsCloudFunctionServiceLog4jConfigFile();
				}
				//TODO consider generating the first implementation during rounds if no others have been generated (see https://stackoverflow.com/q/27886169 for warning), and maybe checking current dependency list, if any, to support incremental compilation
				if(!consumerTypeElementsByCloudFunctionApiTypeElement.isEmpty()) {
					final Set<ClassName> awsCloudFunctionStubClassNames = new HashSet<>();
					consumerTypeElementsByCloudFunctionApiTypeElement.forEach(throwingBiConsumer((serviceApiTypeElement, serviceConsumerTypeElements) -> {
						awsCloudFunctionStubClassNames.add(generateAwsCloudFunctionStubClass(serviceApiTypeElement, serviceConsumerTypeElements));
					}));
					generateFlangeDependenciesList(DEPENDENCIES_LIST_PLATFORM_AWS_FILENAME, awsCloudFunctionStubClassNames);
				}
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
	protected ClassName generateAwsCloudFunctionStubClass(@Nonnull final TypeElement serviceApiTypeElement, @Nonnull Set<TypeElement> serviceConsumerTypeElements)
			throws IOException {
		final ClassName serviceApiClassName = ClassName.get(serviceApiTypeElement);
		//TODO this probably won't work correctly with incremental compiling, so find a consistent way to determine a good package name for the stub, or take care not to replace a previous one during incremental compilation
		final String lambdaStubPackageName = findFirst(serviceConsumerTypeElements).map(ClassName::get).map(ClassName::packageName)
				.orElseThrow(() -> new IllegalArgumentException("No service API consumers given."));
		final ClassName awsLambdaStubClassName = ClassName.get(lambdaStubPackageName, serviceApiClassName.simpleName() + AWS_CLOUD_FUNCTION_STUB_CLASS_NAME_SUFFIX);
		//`public final class ServiceApi_FlangeAwsLambdaStub implements ServiceApi …`
		final TypeSpec.Builder lambdaStubClassSpecBuilder = TypeSpec.classBuilder(awsLambdaStubClassName).addOriginatingElement(serviceApiTypeElement) //
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL).superclass(ABSTRACT_AWS_CLOUD_FUNCTION_API_STUB_CLASS_NAME).addSuperinterface(serviceApiClassName) //
				.addJavadoc("AWS Lambda stub for {@link $T}.", serviceApiClassName);
		final DeclaredType futureUnboundedWildcardType = findUnboundedWildcardDeclaredType(processingEnv, Future.class).orElseThrow(IllegalStateException::new);
		final DeclaredType completableFutureUnboundedWildcardType = findUnboundedWildcardDeclaredType(processingEnv, CompletableFuture.class)
				.orElseThrow(IllegalStateException::new);
		methodsIn(serviceApiTypeElement.getEnclosedElements()).forEach(methodElement -> {
			if(methodElement.isDefault()) { //don't override default method implementations
				return;
			}
			final String methodName = methodElement.getSimpleName().toString();
			final TypeMirror returnTypeMirror = methodElement.getReturnType();
			if(returnTypeMirror instanceof final DeclaredType returnDeclaredType
					&& (processingEnv.getTypeUtils().isAssignable(returnTypeMirror, futureUnboundedWildcardType) //Future<?>
							|| processingEnv.getTypeUtils().isAssignable(returnTypeMirror, completableFutureUnboundedWildcardType) //CompletableFuture<?>
			)) {
				final TypeMirror typeArgument = getOnly(returnDeclaredType.getTypeArguments(), IllegalStateException::new); //CompletableFuture<?> is expected to only have one type argument
				final MethodSpec.Builder methodSpecBuilder = MethodSpec.overriding(methodElement); //TODO comment
				methodSpecBuilder.addStatement("return invokeAsync(new $T<$L>(){}, $S)", GenericType.class, typeArgument, methodName); //TODO finish; add parameters
				lambdaStubClassSpecBuilder.addMethod(methodSpecBuilder.build());
			} else {
				processingEnv.getMessager().printMessage(ERROR,
						"Currently only `Future<?>` or `CompletableFuture<?>` return types are supported for cloud function APIs; found `%s`.".formatted(returnTypeMirror));
			}
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
	protected ClassName generateAwsCloudFunctionServiceSkeletonClass(@Nonnull final TypeElement serviceImplTypeElement) throws IOException {
		final ClassName serviceImplClassName = ClassName.get(serviceImplTypeElement);
		final ClassName awsLambdaHandlerClassName = ClassName.get(serviceImplClassName.packageName(),
				serviceImplClassName.simpleName() + AWS_CLOUD_FUNCTION_SKELETON_CLASS_NAME_SUFFIX);
		final TypeName awsLambdaHandlerSuperClassName = ParameterizedTypeName.get(AWS_CLOUD_FUNCTION_SERVICE_HANDLER_CLASS_NAME, serviceImplClassName);
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
	 * @param cloudFunctionServiceImplTypeElement The element representing the implementation of the FaaS service for which an assembly will be created.
	 * @throws IOException if there is an I/O error writing the assembly descriptor.
	 */
	protected void generateCloudFunctionServiceAwsLambdaAssemblyDescriptor(@Nonnull final TypeElement cloudFunctionServiceImplTypeElement) throws IOException {
		final String assemblyDescriptorFilename = "assembly-" + cloudFunctionServiceImplTypeElement.getSimpleName() + "-aws-lambda.xml"; //TODO use constant
		try (
				final InputStream inputStream = findResourceAsStream(getClass(), RESOURCE_ASSEMBLY_DESCRIPTOR_AWS_LAMBDA)
						.orElseThrow(() -> new ConfiguredStateException("Missing class resource `%s`.".formatted(RESOURCE_ASSEMBLY_DESCRIPTOR_AWS_LAMBDA)));
				final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
			final FileObject outputFileObject = processingEnv.getFiler().createResource(SOURCE_OUTPUT, "", assemblyDescriptorFilename,
					cloudFunctionServiceImplTypeElement);
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
	 * Generates the Flange dependencies list indicating stub or backing service implementations used by the AWS Lambda handlers.
	 * @apiNote Typically a dependencies list will be created for immediate dependencies such as backing service implementations, or platform-specific stub
	 *          implementation stubs for remotely accessing services.
	 * @param relativeResourcePath The filename or path of the dependencies list, relative to the source output.
	 * @param dependencyClassNames The class names of the stubs or backing service implementations.
	 * @throws IOException if there is an I/O error writing the dependencies list.
	 */
	protected void generateFlangeDependenciesList(@Nonnull final CharSequence relativeResourcePath, @Nonnull final Set<ClassName> dependencyClassNames)
			throws IOException {
		final FileObject outputFileObject = processingEnv.getFiler().createResource(SOURCE_OUTPUT, "", relativeResourcePath);
		try (final Writer writer = new OutputStreamWriter(new BufferedOutputStream(outputFileObject.openOutputStream()), UTF_8)) {
			for(final ClassName dependencyClassName : dependencyClassNames) {
				writer.write(dependencyClassName.canonicalName());
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
	protected void generateAwsCloudFunctionServiceLog4jConfigFile() throws IOException {
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
	 * @param awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames The class names of the service implementations and generated AWS Lambda handler skeleton
	 *          classes; each pair associated with the class name of the service API class, which is typically an interface.
	 * @throws IOException if there is an I/O error writing the SAM template.
	 */
	protected void generateAwsSamTemplate(
			@Nonnull final Map<ClassName, Map.Entry<ClassName, ClassName>> awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames) throws IOException {
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
				awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames.forEach(throwingBiConsumer((serviceApiClassName, serviceImplSkeletonClassNameEntry) -> {
					writer.write("  %s:%n".formatted(serviceApiClassName.simpleName().replace("_", ""))); //TODO refactor using logical ID sanitizing method
					writer.write("    Type: AWS::Serverless::Function%n".formatted());
					writer.write("    Properties:%n".formatted());
					writer.write("      FunctionName: !Sub \"flange-${Env}-%s\"%n".formatted(serviceApiClassName.simpleName())); //TODO consider appending short hash of fully-qualified package+class to prevent clashes 
					writer.write("      CodeUri:%n".formatted());
					writer.write("        Bucket:%n".formatted());
					writer.write("          Fn::ImportValue:%n".formatted());
					writer.write("            !Sub \"flange-${Env}:StagingBucketName\"%n".formatted());
					writer.write("        Key: !Sub \"%s-aws-lambda.zip\"%n".formatted(serviceImplSkeletonClassNameEntry.getKey().simpleName())); //TODO later interpolate version 
					writer.write("      Handler: %s::%s%n".formatted(serviceImplSkeletonClassNameEntry.getValue().canonicalName(), "handleRequest")); //TODO use constant
					//TODO add environment variable for active profiles
				}));
			}
		}
	}

	//## Elements

	/**
	 * Retrieves an annotation mirror from a type element for annotations of a particular type.
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

	//TODO document, comparing with `annotationMirrors(…)`
	static Stream<? extends TypeMirror> interfacesAssignableTo(@Nonnull final Elements elements, @Nonnull final Types types, //TODO perhaps delete; `interfacesAnnotatedWith` was probably desired
			@Nonnull final TypeElement typeElement, @Nonnull final Class<?> interfaceClass) {
		checkArgument(interfaceClass.isInterface(), "Class `%s` does not represent an interface.", interfaceClass.getName());
		return typeElement.getInterfaces().stream().filter(isAssignableTo(elements, types, interfaceClass));
	}

	//TODO document
	static Stream<DeclaredType> interfacesAnnotatedWith(@Nonnull final Elements elements, @Nonnull final Types types, @Nonnull final TypeElement typeElement,
			@Nonnull final Class<? extends Annotation> annotationClass) {
		return typeElement.getInterfaces().stream().flatMap(asInstances(DeclaredType.class))
				.filter(interfaceType -> findAnnotationMirror((TypeElement)interfaceType.asElement(), annotationClass).isPresent());
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

	//TODO probably move a lot of the methods requiring `Elements` and `Types` up to the `Elements` section, as many of them are so fundamental (and the utilities are part of `javax.lang.model.util`), even though they assume some processing environment to get a utilities instance 

	/**
	 * Tests whether a type is assignable to the type corresponding to the given class (i.e. whether instances of each would have an <code>instanceof</code>
	 * relationship).
	 * @implSpec This implementation calls {@link #findDeclaredType(Elements, Types, Class, TypeMirror...)}.
	 * @param elements The element utilities.
	 * @param types The type utilities.
	 * @param typeMirror The type to test.
	 * @param clazz The class representing the type against which to compare for assignability.
	 * @return <code>true</code> if the type is assignable to the type represented by the class.
	 * @throws IllegalArgumentException if no type could be found for the given class; or given a type for an executable, package, or module is invalid.
	 * @see Types#isAssignable(TypeMirror, TypeMirror)
	 */
	static boolean isAssignableTo(@Nonnull final Elements elements, @Nonnull final Types types, TypeMirror typeMirror, @Nonnull final Class<?> clazz) {
		/*TODO delete probably
				return types.isAssignable(typeMirror, findDeclaredType(elements, types, clazz)
						.orElseThrow(() -> new IllegalArgumentException("No declared type found for class `%s`.`".formatted(clazz.getName()))));
		*/
		return isAssignableTo(elements, types, clazz).test(typeMirror);
	}

	static Predicate<TypeMirror> isAssignableTo(@Nonnull final Elements elements, @Nonnull final Types types, @Nonnull final Class<?> clazz) { //TODO document
		final TypeMirror classType = findDeclaredType(elements, types, clazz)
				.orElseThrow(() -> new IllegalArgumentException("No declared type found for class `%s`.`".formatted(clazz.getName())));
		return type -> types.isAssignable(type, classType);
	}

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
	static Optional<DeclaredType> findDeclaredType(@Nonnull final Elements elements, @Nonnull final Types types, @Nonnull final Class<?> clazz,
			@Nonnull final TypeMirror... typeArgs) {
		return findTypeElement(elements, clazz).map(typeElement -> types.getDeclaredType(typeElement, typeArgs));
	}

	/**
	 * Finds a returns a type corresponding to a class type element and actual type arguments, if the type element is uniquely determinable in the environment.
	 * @implSpec This implementation delegates to {@link #findDeclaredType(Elements, Types, Class, TypeMirror...)}.
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
	static Optional<DeclaredType> findDeclaredType(@Nonnull ProcessingEnvironment processingEnvironment, @Nonnull final Class<?> clazz,
			@Nonnull final TypeMirror... typeArgs) {
		return findDeclaredType(processingEnvironment.getElementUtils(), processingEnvironment.getTypeUtils(), clazz, typeArgs);
	}

	/**
	 * Finds a returns a type corresponding to a class type element with an unbounded wildcard type parameter, if the type element is uniquely determinable in the
	 * environment.
	 * @implSpec This implementation delegates to {@link #findDeclaredType(Elements, Types, Class, TypeMirror...)} using the result of
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
	static Optional<DeclaredType> findUnboundedWildcardDeclaredType(@Nonnull final Elements elements, @Nonnull final Types types, @Nonnull final Class<?> clazz) {
		return findDeclaredType(elements, types, clazz, getUnboundedWildcardType(types));
	}

	/**
	 * Finds a returns a type corresponding to a class type element with an unbounded wildcard type parameter, if the type element is uniquely determinable in the
	 * environment.
	 * @implSpec This implementation delegates to {@link #findUnboundedWildcardDeclaredType(Elements, Types, Class)}.
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
	static Optional<DeclaredType> findUnboundedWildcardDeclaredType(@Nonnull ProcessingEnvironment processingEnvironment, @Nonnull final Class<?> clazz) {
		return findUnboundedWildcardDeclaredType(processingEnvironment.getElementUtils(), processingEnvironment.getTypeUtils(), clazz);
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
