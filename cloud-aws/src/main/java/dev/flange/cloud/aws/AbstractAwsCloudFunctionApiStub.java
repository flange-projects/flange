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
import static dev.flange.cloud.aws.AwsLambda.*;
import static dev.flange.cloud.aws.Marshalling.*;
import static java.util.Arrays.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import javax.annotation.*;

import com.fasterxml.classmate.*;
import com.fasterxml.jackson.core.JsonProcessingException;

import dev.flange.cloud.*;
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
				.reduce(toFindOnly(() -> new IllegalStateException("Multiple interfaces annotated with `@%s` not supported for stub class `%s`."
						.formatted(CloudFunctionApi.class.getSimpleName(), stubClass.getName()))))
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
	 * @implNote Because of how remote invocation works, all exceptions thrown by the invoked implementation will be returned as a {@link CompletableFuture} error
	 *           (i.e it will not be thrown directly), even if the implementation behind the skeleton threw an error directly before creating a
	 *           {@link CompletableFuture}. This is because remoting inherently adds another layer of {@link CompletableFuture}, and invoking the actual
	 *           implementation on the skeleton side occurs after the stub has already created its {@link CompletableFuture}.
	 * @param <T> The type of value to be returned by the future.
	 * @param genericReturnType Generics-aware information about the return type of the future.
	 * @param methodName The name of the method to be invoked.
	 * @param methodArguments The arguments of method to be marshalled.
	 * @return The future return value.
	 */
	protected <T> CompletableFuture<T> invokeAsync(@Nonnull GenericType<T> genericReturnType, @Nonnull final String methodName,
			@Nonnull final Object... methodArguments) {
		//TODO create a wrapped CompleteableFuture that, when using a timeout, passes the timeout across the wire to the lambda invocation to use in the CompleteableFuture on the other side
		//TODO fix		return CompletableFuture.supplyAsync(() -> invoke(genericReturnType, methodName, methodArguments));
		return CompletableFuture.supplyAsync(() -> {
			try {
				return invoke(genericReturnType, methodName, methodArguments);
				//TODO determine how to deal with remote `CompletionException`
			} catch(final InvocationTargetException invocationTargetExcption) {
				//Wrap _all_ invocations in the invoked method in a `CompletionException`.
				//It would never be appropriate to throw the wrapped exception directly here,
				//even if it matched the calling method signature, because we are inside a
				//concurrent operation and the originally caller has already returned.
				//Moreover rethrowing the wrapped exception here would simply result in its
				//being wrapped in a `CompletionException` anyway.
				throw new CompletionException(invocationTargetExcption.getCause());
			}
		});
	}

	/**
	 * Invokes a cloud function synchronously, directly returning the result.
	 * @apiNote Passing back remote method exceptions using {@link InvocationTargetException} could be considered a slight misuse of the class, as no reflective
	 *          operation is being performed. A broader definition of "reflection" however might consider remote method invocation to be form of "reflection over
	 *          a distance". Pragmatically {@link InvocationTargetException} reflects the semantics of wrapping the exception thrown by some dynamically invoked
	 *          method, and creating a new exception type for this seems to provide little value.
	 * @implNote If this implementation at some point has the possibility to throw an {@link InvocationTargetException} via actual reflection calls, the exception
	 *           used to pass back remote invocation exceptions will need to be changed to distinguish between the two cases.
	 * @param <T> The type of value to be returned.
	 * @param genericReturnType Generics-aware information about the type of the return value.
	 * @param methodName The name of the method to be invoked.
	 * @param methodArguments The arguments of method to be marshalled.
	 * @return The value returned from the invocation.
	 * @throws InvocationTargetException if the invoked remote method threw an exception; the exception throw may be retrieved using
	 *           {@link InvocationTargetException#getCause()}, which in this context will never be <code>null</code>.
	 * @throws FlangeMarshalException if any error related to marshalling occurs, including general I/O, serialization/deserialization, and data
	 *           conversion/mapping.
	 * @throws FlangeCloudException if any other error occurred relating to invoking AWS Lambda.
	 */
	protected <T> T invoke(@Nonnull GenericType<T> genericReturnType, @Nonnull final String methodName, @Nonnull final Object... methodArguments)
			throws InvocationTargetException, FlangeMarshalException, FlangeCloudException {
		//TODO add qualifier if needed
		final Map<String, Object> input = Map.of(PARAM_FLANGE_METHOD_NAME, methodName, PARAM_FLANGE_METHOD_ARGS, asList(methodArguments)); //target method args may contain `null`
		final String inputJson;
		try {
			inputJson = JSON_WRITER.writeValueAsString(input);
		} catch(final JsonProcessingException jsonProcessingException) {
			throw new FlangeMarshalException("Unexpected error serializing JSON.", jsonProcessingException);
		}
		final SdkBytes requestPayload = SdkBytes.fromUtf8String(inputJson);
		final InvokeRequest request = InvokeRequest.builder().functionName(getSkeletonCloudFunctionName(methodName)).invocationType(InvocationType.REQUEST_RESPONSE)
				.payload(requestPayload).build();
		final InvokeResponse response;
		try {
			response = getLambdaClient().invoke(request);
		} catch(final LambdaException lambdaException) {
			throw new FlangeCloudException(
					"Got lambda exception; AWS error message: `%s`; status code: `%s`".formatted(lambdaException.awsErrorDetails(), lambdaException.statusCode()),
					lambdaException); //TODO retry as needed based upon the error
		}
		try {
			final int responseStatusCode = response.statusCode();
			final Optional<String> foundResponseFunctionError = Optional.ofNullable(response.functionError());
			return switch(responseStatusCode) {
				case STATUS_CODE_REQUEST_RESPONSE_SUCCESS -> {
					if(FUNCTION_ERROR_UNHANDLED.equals(response.functionError())) { //wrap and rethrow any error originating in the invoked method
						final Throwable unhandledThrowable = unmarshalUnhandledError(response.payload().asInputStream()).toThrowable();
						if(unhandledThrowable instanceof MarshalledThrowable marshalledThrowable) { //if some remote exception (checked or unchecked) was caught and marshalled
							throw new InvocationTargetException(marshalledThrowable.getCause()); //unwrap the marshalled exception
						}
						throw new InvocationTargetException(unhandledThrowable); //TODO consider augmenting the marshalled stack trace with the filled in stack trace at this point, or whether to make the additional marshalling layers transparent
					}
					yield unmarshalJson(response.payload().asInputStream(), genericReturnType);
				}
				default -> throw new FlangeCloudException("AWS Lambda invocation failed with a status `%d`: %s".formatted(responseStatusCode,
						foundResponseFunctionError.orElse("(no function error given)")));
			};
		} catch(final IOException ioException) {
			//TODO devise a way to marshal back an actual `IOException` that occur on the caller side
			throw new FlangeMarshalException("Error parsing AWS Lambda response JSON: " + ioException.getMessage(), ioException);
		}
	}

}
