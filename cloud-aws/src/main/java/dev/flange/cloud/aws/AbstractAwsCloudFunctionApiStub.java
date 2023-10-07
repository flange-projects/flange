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

package dev.flange.cloud.aws;

import static com.globalmentor.util.stream.Streams.*;
import static dev.flange.cloud.aws.Marshalling.*;
import static java.util.Arrays.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.annotation.*;

import com.fasterxml.classmate.*;
import com.fasterxml.jackson.core.JsonProcessingException;

import dev.flange.cloud.CloudFunctionApi;
import io.clogr.Clogged;
import io.confound.Confounded;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;

/**
 * Abstract base class for implementing an AWS Lambda stub for a FaaS API.
 * @author Garret Wilson
 */
public abstract class AbstractAwsCloudFunctionApiStub implements Clogged, Confounded {

	private final LambdaClient lambdaClient;

	/** @return The client for communicating with AWS Lambda. */
	protected LambdaClient getLambdaClient() {
		return lambdaClient;
	}

	/**
	 * Returns the name of the cloud function to invoke from this stub.
	 * @implSpec This implementation ignores the given method name, as a Flange FaaS-based service uses a single handler.
	 * @param methodName The name of the method to be invoked.
	 * @return The AWS Lambda function handler name.
	 */
	protected String getSkeletonCloudFunctionName(@Nonnull final String methodName) {
		final Class<?> stubClass = getClass();
		//TODO improve by caching the service API class and/or initializing the base class with the cloud function name, although this may pose problems with an environment name placeholder; keep in mind supporting other types of stubs in the future, e.g. for `@CloudFunctionApplication`, or `@CloudFunctions` representing a collection of functions
		final Class<?> serviceApiClass = Stream.of(stubClass.getInterfaces()).filter(interfaceClass -> interfaceClass.isAnnotationPresent(CloudFunctionApi.class)) //TODO create utility method for finding annotated interface 
				.reduce(toFindOnly(() -> new IllegalStateException(
						"Multiple interfaces annotated with `@%s` not supported for stub class `%s`.".formatted(CloudFunctionApi.class.getSimpleName(), stubClass.getName()))))
				.orElseThrow(() -> new IllegalStateException("Stub class `%s` does not seem to implement a service API, e.g. annotated with `@%s`."
						.formatted(stubClass.getName(), CloudFunctionApi.class.getSimpleName())));
		final String env = getConfiguration().getString("flange.env"); //TODO use constant
		return "flange-%s-%s".formatted(env, serviceApiClass.getSimpleName());
	}

	/** Constructor. */
	public AbstractAwsCloudFunctionApiStub() {
		lambdaClient = LambdaClient.builder().build();
	}

	/**
	 * Invokes a cloud function asynchronously, returning a completable future future for the result.
	 * @param <T> The type of value to be returned by the future.
	 * @param genericReturnType Generics-aware information about the return type of the future.
	 * @param methodName The name of the method to be invoked.
	 * @param methodArguments The arguments of method to be marshalled.
	 * @return The future return value.
	 */
	protected <T> CompletableFuture<T> invokeAsync(@Nonnull GenericType<T> genericReturnType, @Nonnull final String methodName,
			@Nonnull final Object... methodArguments) {
		//TODO create a wrapped CompleteableFuture that, when using a timeout, passes the timeout across the wire to the lambda invocation to use in the CompleteableFuture on the other side
		return CompletableFuture.supplyAsync(() -> invoke(genericReturnType, methodName, methodArguments));
	}

	/**
	 * Invokes a cloud function synchronously, directly returning the result.
	 * @param <T> The type of value to be returned.
	 * @param genericReturnType Generics-aware information about the type of the return value.
	 * @param methodName The name of the method to be invoked.
	 * @param methodArguments The arguments of method to be marshalled.
	 * @return The value returned from the invocation.
	 */
	protected <T> T invoke(@Nonnull GenericType<T> genericReturnType, @Nonnull final String methodName, @Nonnull final Object... methodArguments) {
		//TODO add qualifier if needed
		final Map<String, Object> input = Map.of(PARAM_FLANGE_METHOD_NAME, methodName, PARAM_FLANGE_METHOD_ARGS, asList(methodArguments)); //target method args may contain `null`
		final String inputJson;
		try {
			inputJson = JSON_WRITER.writeValueAsString(input);
		} catch(final JsonProcessingException jsonProcessingException) {
			throw new RuntimeException("Unexpected error serializing JSON.", jsonProcessingException); //TODO improve
		}
		//TODO delete System.out.println("request payload JSON: " + inputJson); //TODO delete
		final SdkBytes requestPayload = SdkBytes.fromUtf8String(inputJson);
		final InvokeRequest request = InvokeRequest.builder().functionName(getSkeletonCloudFunctionName(methodName)).invocationType(InvocationType.REQUEST_RESPONSE)
				.payload(requestPayload).build();
		final InvokeResponse response;
		try {
			response = lambdaClient.invoke(request);
		} catch(final LambdaException lambdaException) {
			getLogger().error("Got lambda exception; AWS error message: {}; status code: {}", lambdaException.awsErrorDetails(), lambdaException.statusCode(),
					lambdaException);//TODO delete
			throw lambdaException; //TODO retry as needed based upon the error
		}
		//TODO catch LamdaException if necessary; we should retry some of these
		final String responsePayloadJson = response.payload().asUtf8String();
		//TODO delete System.out.println("response payload JSON: " + responsePayloadJson); //TODO delete

		try {
			//check for error as per [AWS Lambda function errors in Java](https://docs.aws.amazon.com/lambda/latest/dg/java-exceptions.html)
			return jsonReaderForType(genericReturnType).readValue(responsePayloadJson);
		} catch(final IOException ioException) {
			throw new RuntimeException("Error parsing AWS Lambda response JSON: " + ioException.getMessage(), ioException); //TODO decide on best type of exception
		}
	}

}
