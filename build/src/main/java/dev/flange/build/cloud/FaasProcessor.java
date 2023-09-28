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

import static com.globalmentor.io.ClassResources.*;
import static java.lang.System.*;
import static java.nio.charset.StandardCharsets.*;
import static javax.tools.Diagnostic.Kind.*;
import static javax.tools.StandardLocation.*;

import java.io.*;
import java.util.*;

import javax.annotation.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.tools.FileObject;

import com.globalmentor.model.ConfiguredStateException;
import com.squareup.javapoet.*;

/**
 * Annotation processor for FaaS.
 * @author Garret Wilson
 */
@SupportedAnnotationTypes("dev.flange.cloud.FaasService")
public class FaasProcessor extends AbstractProcessor {

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
	 * @implSpec This processor supports the latest supported source version.
	 */
	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	private static final ClassName FAAS_SERVICE_LAMBDA_HANDLER_CLASS_NAME = ClassName.get("dev.flange.cloud.aws", "FaasServiceLambdaHandler");

	private static final String LAMBDA_HANDLER_CLASS_NAME_SUFFIX = "_FlangeLambdaHandler";

	private final Set<ClassName> processedFaasServiceLambdaImplClassNames = new HashSet<>();
	private final Set<ClassName> processedFaasServiceLambdaHandlerClassNames = new HashSet<>();

	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnvironment) {
		try {
			for(TypeElement annotation : annotations) {
				final Set<? extends Element> annotatedElements = roundEnvironment.getElementsAnnotatedWith(annotation);
				final Set<TypeElement> typeElements = ElementFilter.typesIn(annotatedElements);
				//TODO raise error if there are non-type elements
				for(final TypeElement typeElement : typeElements) {
					final ClassName lambdaImplClassName = ClassName.get(typeElement);
					final ClassName lambdaHandlerClassName = generateFaasServiceLambdaStubClass(typeElement);
					processedFaasServiceLambdaImplClassNames.add(lambdaImplClassName);
					processedFaasServiceLambdaHandlerClassNames.add(lambdaHandlerClassName);
					generateFaasServiceLambdaAssemblyDescriptor(typeElement);
				}
			}
			if(roundEnvironment.processingOver()) {
				generateFlangeDependenciesList(processedFaasServiceLambdaImplClassNames);
				generateFaasServiceSamTemplate(processedFaasServiceLambdaHandlerClassNames);
				generateFaasServiceLambdaLog4jConfigFile();
			}
		} catch(final IOException ioException) {
			processingEnv.getMessager().printMessage(ERROR, ioException.getMessage()); //TODO improve
		}

		return true;
	}

	/**
	 * Generates the AWS Lambda stub class for a FaaS service implementation.
	 * @param faasServiceImplTypeElement The element representing the implementation of the FaaS service to be invoked by the stub.
	 * @throws IOException if there is an I/O error writing the class.
	 * @return The class name of the generated stub class.
	 */
	protected ClassName generateFaasServiceLambdaStubClass(@Nonnull final TypeElement faasServiceImplTypeElement) throws IOException {
		final ClassName lambdaImplClassName = ClassName.get(faasServiceImplTypeElement);
		final ClassName lambdaHandlerClassName = ClassName.get(lambdaImplClassName.packageName(),
				lambdaImplClassName.simpleName() + LAMBDA_HANDLER_CLASS_NAME_SUFFIX);
		final TypeName lambdaHandlerSuperClassName = ParameterizedTypeName.get(FAAS_SERVICE_LAMBDA_HANDLER_CLASS_NAME, lambdaImplClassName);
		//`public final class MyServiceImpl_FlangeLambdaHandler …`
		final TypeSpec lambdaHandlerClassSpec = TypeSpec.classBuilder(lambdaHandlerClassName).addOriginatingElement(faasServiceImplTypeElement) //
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL).superclass(lambdaHandlerSuperClassName) //
				.addJavadoc("AWS Lambda handler stub for {@link $T}.", lambdaImplClassName) //
				.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC) //constructor
						.addJavadoc("Constructor.") //
						.addStatement("super($T.class)", lambdaImplClassName).build())
				.build();
		final JavaFile lambdaHandlerClassJavaFile = JavaFile.builder(lambdaImplClassName.packageName(), lambdaHandlerClassSpec) //
				.addFileComment("""
						File generated by [Flange](https://flange.dev/), a tool by
						[GlobalMentor, Inc.](https://www.globalmentor.com/).

						_Normally this file should not be edited manually._""").build();
		lambdaHandlerClassJavaFile.writeTo(processingEnv.getFiler());
		return lambdaHandlerClassName;
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
		final String dependenciesListFilename = "flange-dependencies.lst"; //TODO reference constant from Flange
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
	 * @param faasServiceLambdaHandlerClassNames The class names of the generated AWS Lambda handler stub classes.
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
					writer.write("      CodeUri: !Sub \"s3://flange-${Env}-staging/%s-aws-lambda.zip\"%n".formatted(faasServiceLambdaHandlerClassName.simpleName())); //TODO switch to imported value reference when templating is available
					writer.write("      Handler: %s::%s%n".formatted(faasServiceLambdaHandlerClassName.canonicalName(), "handleRequest")); //TODO use constant
					//TODO add environment variable for active profiles
				}
			}
		}
	}

}
