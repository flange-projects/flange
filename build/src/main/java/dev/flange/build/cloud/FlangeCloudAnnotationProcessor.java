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
import static com.globalmentor.java.Objects.*;
import static com.globalmentor.java.model.ModelElements.*;
import static com.globalmentor.java.model.ModelTypes.*;
import static com.globalmentor.util.stream.Streams.*;
import static dev.flange.cloud.aws.FlangePlatformAws.*;
import static java.lang.System.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;
import static javax.lang.model.util.ElementFilter.*;
import static javax.tools.Diagnostic.Kind.*;
import static javax.tools.StandardLocation.*;
import static org.zalando.fauxpas.FauxPas.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.annotation.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.FileObject;

import com.fasterxml.classmate.GenericType;
import com.globalmentor.java.model.BaseAnnotationProcessor;
import com.globalmentor.model.ConfiguredStateException;
import com.squareup.javapoet.*;

import dev.flange.cloud.*;
import dev.flange.cloud.aws.*;

/**
 * Annotation processor for Flange Cloud.
 * <p>
 * This annotation processor supports the following annotations:
 * </p>
 * <ul>
 * <li>{@link CloudFunctionService}</li>
 * <li>{@link ServiceConsumer}</li>
 * </ul>
 * @author Garret Wilson
 */
public class FlangeCloudAnnotationProcessor extends BaseAnnotationProcessor {

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

	/** Default constructor. */
	public FlangeCloudAnnotationProcessor() {
		super(Set.of(CloudFunctionService.class, ServiceConsumer.class));
	}

	private static final ClassName ABSTRACT_AWS_CLOUD_FUNCTION_API_STUB_CLASS_NAME = ClassName.get(AbstractAwsCloudFunctionApiStub.class);
	private static final ClassName ABSTRACT_AWS_CLOUD_FUNCTION_SERVICE_HANDLER_CLASS_NAME = ClassName.get(AbstractAwsCloudFunctionServiceHandler.class);

	private static final String AWS_CLOUD_FUNCTION_STUB_CLASS_NAME_SUFFIX = "_FlangeAwsLambdaStub";
	private static final String AWS_CLOUD_FUNCTION_SKELETON_CLASS_NAME_SUFFIX = "_FlangeAwsLambdaSkeleton";

	private static final String DEPENDENCIES_LIST_FILENAME = "flange-dependencies.lst"; //TODO reference constant from Flange
	private static final String DEPENDENCIES_LIST_PLATFORM_AWS_FILENAME = "flange-dependencies_platform-aws.lst"; //TODO reference constant from Flange

	/** The set of all collected classes representing cloud function service implementations. */
	private final Set<ClassName> cloudFunctionServiceImplClassNames = new HashSet<>();

	/** The mapping of service implementation class to skeleton implementation class for each service API. */
	private final Map<ClassName, Map.Entry<ClassName, ClassName>> awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames = new HashMap<>();

	/** For each cloud function API, a set of all consumers that consume it. */
	private final Map<TypeElement, Set<TypeElement>> consumerElementsByConsumedCloudFunctionApiElement = new HashMap<>();

	/** For each consumer class, a set of all cloud function API classes that the consumer consumes. */
	private final Map<ClassName, Set<ClassName>> consumedCloudFunctionApiClassNamesByConsumerClassName = new HashMap<>();

	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnvironment) {
		try {
			//@CloudFunctionService
			{
				final Set<? extends Element> cloudFunctionServiceImplAnnotatedElements = roundEnvironment.getElementsAnnotatedWith(CloudFunctionService.class); //TODO create utility to get types and ensure there are no non-types
				final Set<TypeElement> cloudFunctionServiceImplTypeElements = typesIn(cloudFunctionServiceImplAnnotatedElements);
				//TODO raise error if there are non-type elements
				for(final TypeElement cloudFunctionServiceImplTypeElement : cloudFunctionServiceImplTypeElements) {
					final Optional<DeclaredType> foundCloudFunctionApiAnnotatedInterfaceType = elementInterfacesAnnotatedWith(cloudFunctionServiceImplTypeElement,
							CloudFunctionApi.class).reduce(toFindOnly()); //TODO use improved reduction from JAVA-344 to provide an error if there are multiple interfaces found
					if(!foundCloudFunctionApiAnnotatedInterfaceType.isPresent()) {
						getProcessingEnvironment().getMessager().printMessage(WARNING,
								"Service `%s` implements no interfaces annotated with `@%s`; generated cloud function may not be accessible from other system components."
										.formatted(cloudFunctionServiceImplTypeElement, CloudFunctionApi.class.getSimpleName()));
					}
					final ClassName cloudFunctionServiceImplClassName = ClassName.get(cloudFunctionServiceImplTypeElement);
					cloudFunctionServiceImplClassNames.add(cloudFunctionServiceImplClassName);
					final ClassName serviceApiClassName = foundCloudFunctionApiAnnotatedInterfaceType.map(DeclaredType::asElement).flatMap(asInstance(TypeElement.class))
							.map(ClassName::get).orElse(cloudFunctionServiceImplClassName); //if we can't find an API interface, use the service class name itself, although other components can't find it
					final ClassName awsCloudFunctionServiceImplSkeletonClassName = generateAwsCloudFunctionServiceSkeletonClass(serviceApiClassName,
							cloudFunctionServiceImplTypeElement);
					awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames.put(serviceApiClassName,
							Map.entry(cloudFunctionServiceImplClassName, awsCloudFunctionServiceImplSkeletonClassName));
					generateCloudFunctionServiceAwsLambdaAssemblyDescriptor(cloudFunctionServiceImplTypeElement);
				}
			}
			//@ServiceClient
			{
				final Set<? extends Element> serviceConsumerAnnotatedElements = roundEnvironment.getElementsAnnotatedWith(ServiceConsumer.class);
				final Set<TypeElement> serviceConsumerAnnotatedTypeElements = typesIn(serviceConsumerAnnotatedElements);
				//TODO raise error if there are non-type elements
				for(final TypeElement serviceConsumerTypeElement : serviceConsumerAnnotatedTypeElements) {
					final AnnotationMirror serviceConsumerAnnotationMirror = findElementAnnotationMirrorForClass(serviceConsumerTypeElement, ServiceConsumer.class)
							.orElseThrow(AssertionError::new); //we already know it has the annotation
					final ClassName serviceConsumerClassName = ClassName.get(serviceConsumerTypeElement);
					@SuppressWarnings("unchecked")
					final List<? extends AnnotationValue> serviceClassAnnotationValues = findAnnotationValueElementValue(serviceConsumerAnnotationMirror)
							.map(AnnotationValue::getValue).flatMap(asInstance(List.class)).orElseThrow(() -> new IllegalStateException(
									"The `@%s` annotation of the element `%s` has no array value.".formatted(ServiceConsumer.class.getSimpleName(), serviceConsumerTypeElement)));
					serviceClassAnnotationValues.stream().map(AnnotationValue::getValue).map(Object::toString) //consumed service API class names
							.map(className -> findTypeElementForCanonicalName(className) //consumed service type elements
									.orElseThrow(() -> new IllegalStateException("Unable to find a single type element for service class `%s`.".formatted(className))))
							.forEach(consumedServiceApiTypeElement -> {
								if(findElementAnnotationMirrorForClass(consumedServiceApiTypeElement, CloudFunctionApi.class).isPresent()) {
									//mapping: consumer->(consumed API)
									consumedCloudFunctionApiClassNamesByConsumerClassName.computeIfAbsent(serviceConsumerClassName, __ -> new LinkedHashSet<>())
											.add(ClassName.get(consumedServiceApiTypeElement));
									//reverse mapping: consumed API->(consumer)								
									consumerElementsByConsumedCloudFunctionApiElement.computeIfAbsent(consumedServiceApiTypeElement, __ -> new LinkedHashSet<>())
											.add(serviceConsumerTypeElement);
								}
							});
				}
			}
			if(roundEnvironment.processingOver()) {
				//local dependencies: generated skeleton(s)
				if(!cloudFunctionServiceImplClassNames.isEmpty()) {
					generateFlangeDependenciesList(DEPENDENCIES_LIST_FILENAME, cloudFunctionServiceImplClassNames);
				}
				if(!awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames.isEmpty()) {
					generateAwsSamTemplate(awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames, consumedCloudFunctionApiClassNamesByConsumerClassName);
					generateAwsCloudFunctionServiceLog4jConfigFile();
				}
				//dependencies to other services: stub(s)
				//TODO consider generating the first implementation during rounds if no others have been generated (see https://stackoverflow.com/q/27886169 for warning), and maybe checking current dependency list, if any, to support incremental compilation
				if(!consumerElementsByConsumedCloudFunctionApiElement.isEmpty()) {
					final Set<ClassName> awsCloudFunctionStubClassNames = new HashSet<>();
					consumerElementsByConsumedCloudFunctionApiElement.forEach(throwingBiConsumer((serviceApiTypeElement, serviceConsumerTypeElements) -> {
						awsCloudFunctionStubClassNames.add(generateAwsCloudFunctionStubClass(serviceApiTypeElement, serviceConsumerTypeElements));
					}));
					generateFlangeDependenciesList(DEPENDENCIES_LIST_PLATFORM_AWS_FILENAME, awsCloudFunctionStubClassNames);
				}
			}
		} catch(final IOException ioException) {
			getProcessingEnvironment().getMessager().printMessage(ERROR, ioException.getMessage()); //TODO improve
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
		final DeclaredType futureUnboundedWildcardType = findDeclaredTypeWithUnboundedWildcardForClass(Future.class).orElseThrow(IllegalStateException::new);
		final DeclaredType completableFutureUnboundedWildcardType = findDeclaredTypeWithUnboundedWildcardForClass(CompletableFuture.class)
				.orElseThrow(IllegalStateException::new);
		methodsIn(serviceApiTypeElement.getEnclosedElements()).forEach(methodElement -> {
			if(methodElement.isDefault()) { //don't override default method implementations
				return;
			}
			final String methodName = methodElement.getSimpleName().toString();
			final TypeMirror returnTypeMirror = methodElement.getReturnType();
			if(returnTypeMirror instanceof final DeclaredType returnDeclaredType
					&& (getProcessingEnvironment().getTypeUtils().isAssignable(returnTypeMirror, futureUnboundedWildcardType) //Future<?> TODO improve check with utility
							|| getProcessingEnvironment().getTypeUtils().isAssignable(returnTypeMirror, completableFutureUnboundedWildcardType) //CompletableFuture<?>
			)) {
				final TypeMirror typeArgument = getOnly(returnDeclaredType.getTypeArguments(), IllegalStateException::new); //CompletableFuture<?> is expected to only have one type argument
				final MethodSpec.Builder methodSpecBuilder = MethodSpec.overriding(methodElement); //TODO comment
				final String argsAsSuffix = !methodSpecBuilder.parameters.isEmpty()
						? methodSpecBuilder.parameters.stream().map(param -> param.name).collect(joining(", ", ", ", "")) //`, …`
						: ""; //if there are no args, don't include any suffix, not even a comma
				methodSpecBuilder.addStatement("return invokeAsync(new $T<$L>(){}, $S$L)", GenericType.class, typeArgument, methodName, argsAsSuffix);
				lambdaStubClassSpecBuilder.addMethod(methodSpecBuilder.build());
			} else {
				getProcessingEnvironment().getMessager().printMessage(ERROR,
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
		lambdaHandlerClassJavaFile.writeTo(getProcessingEnvironment().getFiler());
		return awsLambdaStubClassName;
	}

	/**
	 * Generates the AWS Lambda skeleton class for a FaaS service implementation.
	 * @param serviceApiClassName The name of the class representing the service API; typically the interface the service implementation implements.
	 * @param serviceImplTypeElement The element representing the implementation of the FaaS service to be invoked by the skeleton.
	 * @throws IOException if there is an I/O error writing the class.
	 * @return The class name of the generated skeleton class.
	 */
	protected ClassName generateAwsCloudFunctionServiceSkeletonClass(@Nonnull final ClassName serviceApiClassName,
			@Nonnull final TypeElement serviceImplTypeElement) throws IOException {
		final ClassName serviceImplClassName = ClassName.get(serviceImplTypeElement);
		final ClassName awsLambdaHandlerClassName = ClassName.get(serviceImplClassName.packageName(),
				serviceImplClassName.simpleName() + AWS_CLOUD_FUNCTION_SKELETON_CLASS_NAME_SUFFIX);
		final TypeName awsLambdaHandlerSuperClassName = ParameterizedTypeName.get(ABSTRACT_AWS_CLOUD_FUNCTION_SERVICE_HANDLER_CLASS_NAME, serviceApiClassName,
				serviceImplClassName);
		//`public final class MyServiceImpl_FlangeLambdaHandler extends AbstractAwsCloudFunctionServiceHandler …`
		final TypeSpec awsLambdaHandlerClassSpec = TypeSpec.classBuilder(awsLambdaHandlerClassName).addOriginatingElement(serviceImplTypeElement) //
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL).superclass(awsLambdaHandlerSuperClassName) //
				.addJavadoc("AWS Lambda handler skeleton for {@link $T} service implementation {@link $T}.", serviceApiClassName, serviceImplClassName) //
				.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC) //constructor
						.addJavadoc("Constructor.") //
						.addStatement("super($T.class, $T.class)", serviceApiClassName, serviceImplClassName).build())
				.build();
		final JavaFile awsLambdaHandlerClassJavaFile = JavaFile.builder(serviceImplClassName.packageName(), awsLambdaHandlerClassSpec) //
				.addFileComment("""
						File generated by [Flange](https://flange.dev/), a tool by
						[GlobalMentor, Inc.](https://www.globalmentor.com/).

						_Normally this file should not be edited manually._""") //
				.skipJavaLangImports(true).build();
		awsLambdaHandlerClassJavaFile.writeTo(getProcessingEnvironment().getFiler());
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
			final FileObject outputFileObject = getProcessingEnvironment().getFiler().createResource(SOURCE_OUTPUT, "", assemblyDescriptorFilename,
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
		final FileObject outputFileObject = getProcessingEnvironment().getFiler().createResource(SOURCE_OUTPUT, "", relativeResourcePath);
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
			final FileObject outputFileObject = getProcessingEnvironment().getFiler().createResource(SOURCE_OUTPUT, "", log4jConfigFilename);
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
	 * Generates an AWS CloudFormation reference in the form <code>!Sub …</code> to reference the AWS Lambda function representing the indicated service API.
	 * @param serviceApiClassName The class name of the service API the function represents.
	 * @return A reference, suitable for placing in a CloudFormation template, to the AWS Lambda function representing the indicated service API.
	 */
	private static String awsCloudFormationServiceApiFunctionNameReference(@Nonnull final ClassName serviceApiClassName) {
		return "!Sub \"flange-${FlangeEnv}-%s\"".formatted(serviceApiClassName.simpleName()); //TODO consider appending short hash of fully-qualified package+class to prevent clashes
	}

	/**
	 * Generates the SAM template for deploying a FaaS service implementation.
	 * @param awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames The class names of the service implementations and generated AWS Lambda handler skeleton
	 *          classes; each pair associated with the class name of the service API class, which is typically an interface.
	 * @param consumedCloudFunctionApiClassNamesByConsumerClassName For each consumer class, a set of all cloud function API classes that the consumer consumes.
	 * @throws IOException if there is an I/O error writing the SAM template.
	 */
	protected void generateAwsSamTemplate(
			@Nonnull final Map<ClassName, Map.Entry<ClassName, ClassName>> awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames,
			@Nonnull final Map<ClassName, Set<ClassName>> consumedCloudFunctionApiClassNamesByConsumerClassName) throws IOException {
		final String samFilename = "sam.yaml"; //TODO use constant
		try (
				final InputStream inputStream = findResourceAsStream(getClass(), RESOURCE_SAM_INTRO)
						.orElseThrow(() -> new ConfiguredStateException("Missing class resource `%s`.".formatted(RESOURCE_SAM_INTRO)));
				final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
			final FileObject outputFileObject = getProcessingEnvironment().getFiler().createResource(SOURCE_OUTPUT, "", samFilename);
			try (final Writer writer = new OutputStreamWriter(new BufferedOutputStream(outputFileObject.openOutputStream()), UTF_8)) {
				String line; //write individual lines to produce line endings consistent with system; note that this will also ensure a trailing newline
				while((line = bufferedReader.readLine()) != null) {
					writer.write(line);
					writer.write(lineSeparator());
				}
				writer.write("%n".formatted()); //TODO eventually parse and interpolate the original YAML file
				writer.write("Resources:%n%n".formatted());
				awsCloudFunctionServiceImplSkeletonsByServiceApiClassNames.forEach(throwingBiConsumer((serviceApiClassName, serviceImplSkeletonClassNameEntry) -> {
					final ClassName serviceImplClassName = serviceImplSkeletonClassNameEntry.getKey();
					final ClassName serviceSkeletonClassName = serviceImplSkeletonClassNameEntry.getValue();
					writer.write("  %s:%n".formatted(serviceApiClassName.simpleName().replace("_", ""))); //TODO refactor using logical ID sanitizing method
					writer.write("    Type: AWS::Serverless::Function%n".formatted());
					writer.write("    Properties:%n".formatted());
					writer.write("      FunctionName: %s%n".formatted(awsCloudFormationServiceApiFunctionNameReference(serviceApiClassName)));
					writer.write("      CodeUri:%n".formatted());
					writer.write("        Bucket:%n".formatted());
					writer.write("          Fn::ImportValue:%n".formatted());
					writer.write("            !Sub \"flange-${FlangeEnv}:StagingBucketName\"%n".formatted()); //TODO use constant
					writer.write("        Key: !Sub \"%s-aws-lambda.zip\"%n".formatted(serviceImplClassName.simpleName())); //TODO later interpolate version 
					writer.write("      Handler: %s::%s%n".formatted(serviceSkeletonClassName.canonicalName(), "handleRequest")); //TODO use constant
					//see if this service implementation itself depends on other cloud function APIs; if so, we'll need to add permissions
					final Set<ClassName> consumedCloudFunctionApiClassNames = consumedCloudFunctionApiClassNamesByConsumerClassName.get(serviceImplClassName);
					if(consumedCloudFunctionApiClassNames != null && !consumedCloudFunctionApiClassNames.isEmpty()) {
						writer.write("      Policies:%n".formatted());
						consumedCloudFunctionApiClassNames.forEach(throwingConsumer(consumedCloudFunctionApiClassName -> {
							writer.write("        - LambdaInvokePolicy:%n".formatted());
							writer.write("            FunctionName: %s%n".formatted(awsCloudFormationServiceApiFunctionNameReference(consumedCloudFunctionApiClassName)));
						}));
					}
					writer.write("      Environment:%n".formatted());
					writer.write("        Variables:%n".formatted());
					writer.write("          FLANGE_PLATFORM: %s%n".formatted(FlangePlatformAws.ID)); //TODO use constant
					writer.write("          FLANGE_ENV: !Ref %s%n".formatted(CLOUDFORMATION_PARAMETER_FLANGE_ENV)); //TODO use constant
					writer.write("          FLANGE_PROFILES_ACTIVE:%n".formatted());
					writer.write("            Fn::ImportValue:%n".formatted());
					writer.write("              !Sub \"flange-${FlangeEnv}:%s\"%n".formatted(CLOUDFORMATION_PARAMETER_FLANGE_PROFILES_ACTIVE));
				}));
			}
		}
	}

}
