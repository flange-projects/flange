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

import static com.globalmentor.java.Conditions.*;
import static com.globalmentor.java.Objects.*;
import static com.globalmentor.util.stream.Streams.*;
import static dev.flange.cloud.aws.Marshalling.*;
import static java.util.Arrays.*;
import static java.util.Objects.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import javax.annotation.*;

import com.amazonaws.services.lambda.runtime.*;
import com.globalmentor.collections.Maps;

import dev.flange.*;
import dev.flange.cloud.*;
import io.clogr.Clogged;

/**
 * Abstract base class for a AWS Lambda handler for a FaaS service.
 * @param <API> The type of the service API being implemented; usually an interface.
 * @param <S> The type of backing service implementation.
 * @author Garret Wilson
 */
public class AbstractAwsCloudFunctionServiceHandler<API, S> implements RequestStreamHandler, Flanged, Clogged {

	private final Class<API> serviceApiClass;

	/** @return The class representing the service API being implemented; usually an interface. */
	protected Class<API> getServiceApiClass() {
		return serviceApiClass;
	}

	private final S service;

	/** @return The backing service implementation. */
	protected S getService() {
		return service;
	}

	/**
	 * FaaS service class constructor.
	 * @implSpec The service dependency will be looked up and instantiated using Flange.
	 * @param serviceApiClass The class representing the service API being implemented; usually an interface.
	 * @param serviceClass The class representing the type of service implementation to ultimately service FaaS requests.
	 * @throws MissingDependencyException if an appropriate dependency of the requested type could not be found.
	 * @throws DependencyException if there is some general error retrieving or creating the dependency.
	 */
	protected AbstractAwsCloudFunctionServiceHandler(@Nonnull Class<API> serviceApiClass, @Nonnull Class<S> serviceClass) {
		this.serviceApiClass = requireNonNull(serviceApiClass);
		this.service = getDependencyInstanceByType(serviceClass); //this handler is tied to a specific service implementation
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This implementation invokes the named method. If the return type of the indicated method is of {@link Future} one of its subtypes, the future value will be
	 * used, in which case this method will block until the future value is available.
	 * </p>
	 * @param inputStream The marshalled input, including:
	 *          <dl>
	 *          <dt>{@value Marshalling#PARAM_FLANGE_METHOD_NAME}</dt>
	 *          <dd>The name of the method to invoke. There must only be one method in the interface with the given name</dd>
	 *          <dt>{@value Marshalling#PARAM_FLANGE_METHOD_ARGS}</dt>
	 *          <dd>The marshalled form of the arguments to pass to the method.</dd>
	 *          </dl>
	 * @param outputStream Receives the marshalled result of the invocation.
	 * @implNote Unlike the stub, this handler implementation supports any method that returns any type of {@link Future}, not just {@link CompletableFuture}.
	 * @implNote A return value of {@code Optional<>} will be extracted and its value marshalled, using <code>null</code> for <code>Optional.empty()</code>.
	 * @throws IOException if a general I/O error occurs not related to any {@link IOException} thrown by the invoked service.
	 * @throws FlangeMarshalException if any error related to marshalling occurs, including serialization/deserialization and data conversion/mapping, that isn't
	 *           specific to I/O.
	 * @throws MarshalledThrowable if the underlying service method throws a throwable of some sort; this exception serves to marshal that throwable back to the
	 *           stub.
	 * @throws FlangeCloudException if some other general error occurs invoking the service method, such as the service method not being accessible.
	 */
	@Override
	public void handleRequest(final InputStream inputStream, final OutputStream outputStream, final Context context) throws IOException {
		@SuppressWarnings("unchecked")
		final Map<String, Object> inputs = JSON_READER.readValue(new BufferedInputStream(inputStream), Map.class);
		final String methodName = Optional.ofNullable(inputs.get(PARAM_FLANGE_METHOD_NAME)).flatMap(asInstance(String.class))
				.orElseThrow(() -> new IllegalArgumentException("Missing input `%s` method name string.".formatted(PARAM_FLANGE_METHOD_NAME)));
		final List<?> marshalledMethodArgs = Optional.ofNullable(inputs.get(PARAM_FLANGE_METHOD_ARGS)).flatMap(asInstance(List.class))
				.orElseThrow(() -> new IllegalArgumentException("Missing input `%s` method arguments list.".formatted(PARAM_FLANGE_METHOD_ARGS)));
		final Map.Entry<Class<?>, Object> result;
		try {
			result = invokeService(methodName, marshalledMethodArgs);
		} catch(final IllegalArgumentException illegalArgumentException) { //e.g. can't find the method
			throw new FlangeCloudException(illegalArgumentException);
		} catch(final InvocationTargetException invocationTargetException) { //e.g. `IllegalArgumentException` or even `IOException`
			//It doesn't matter whether the result type is a `Future<>` or a direct value;
			//if the invoked method throws an exception, we'll pass it a long wrapped in a `MarshalledThrowable`.
			//It is the responsibility of the stub to decide what to do with the exception.
			//If the method is a direct call, the stub will somehow use the throwable directly (when implemented).
			//If the method returned a future, the stub will wrap the exception as appropriate for the future.
			throw new MarshalledThrowable(invocationTargetException.getCause());
		}
		final Class<?> methodReturnType = result.getKey();
		final Object resultValue;
		if(Future.class.isAssignableFrom(methodReturnType)) {
			final Future<?> futureResult = (Future<?>)result.getValue();
			checkState(futureResult != null, "An invoked method future return type `%s` cannot return `null`.", methodReturnType.getClass().getName());
			try {
				resultValue = futureResult.get(); //TODO use timeout with `get(long timeout, TimeUnit unit)`
				//TODO add retry on the client side for some of these exceptions
			} catch(final CancellationException cancellationException) {
				throw new MarshalledThrowable(
						"Service operation cancelled while invoking class `%s` method `%s`.".formatted(getService().getClass().getName(), methodName),
						cancellationException);
			} catch(final InterruptedException interruptedException) {
				throw new MarshalledThrowable("Interrupted while invoking class `%s` method `%s`.".formatted(getService().getClass().getName(), methodName),
						interruptedException);
			} catch(final ExecutionException executionException) {
				final Throwable cause = executionException.getCause();
				throw new MarshalledThrowable(cause);
			}
		} else { //use a non-`Future<>` result as-is
			resultValue = result.getValue();
		}
		//final ClientContext clientContext = context.getClientContext();	//TODO see if this can provide us information from https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/lambda/model/InvokeRequest.Builder.html#clientContext(java.lang.String)
		final BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
		marshalJson(resultValue, bufferedOutputStream).flush(); //be sure to flush after marshalling
		bufferedOutputStream.flush();
	}

	/**
	 * Invokes a method of the service with the given name, providing the given marshalled method arguments.
	 * @implSpec This implementation unmarshals method arguments using {@link #unmarshalMethodArgs(List, List)}.
	 * @param methodName The name of the service method.
	 * @param marshalledMethodArgs The method arguments as marshalled to the handler; may need further transformation based upon the actual method types.
	 * @return The result of the invocation as a value mapped to the class representing the raw method return type.
	 * @throws IllegalArgumentException if the service has multiple methods with the given name.
	 * @throws FlangeMarshalException if the marshalled method arguments cannot be unmarshalled.
	 * @throws FlangeCloudException if some other general error occurs invoking the service method, such as the service method not being accessible.
	 * @throws InvocationTargetException if the underlying service method being invoked throws an exception.
	 */
	protected Map.Entry<Class<?>, Object> invokeService(@Nonnull final String methodName, @Nonnull final List<?> marshalledMethodArgs)
			throws InvocationTargetException {
		final Class<?> serviceApiClass = getServiceApiClass();
		final Method method;
		try {
			method = declaredMethodsHavingName(serviceApiClass, methodName).collect(toOnly()); //look up method from the API
		} catch(final NoSuchElementException noSuchElementException) {
			throw new IllegalArgumentException("No service API `%s` method named `%s` found.".formatted(serviceApiClass.getName(), methodName));
		} catch(final IllegalArgumentException illegalArgumentException) {
			throw new IllegalArgumentException(
					"Service API `%s` has multiple methods named `%s`; currently only one is currently supported.".formatted(serviceApiClass.getName(), methodName));
		}
		//TODO delete getLogger().atInfo().log("Handling request for method name `{}`.", methodName); //TODO delete
		final List<Type> methodArgTypes = asList(method.getGenericParameterTypes()); //TODO add PLOOP convenience list method
		final List<?> methodArgs;
		try {
			methodArgs = unmarshalMethodArgs(marshalledMethodArgs, methodArgTypes);
		} catch(final IllegalArgumentException illegalArgumentException) {
			throw new FlangeMarshalException("Error marshalling method arguments.", illegalArgumentException);
		}
		try {
			//note that the service implementation might be returning a `Future` subtype via covariance, but that should not cause any problems
			final Object result = method.invoke(getService(), methodArgs.toArray(Object[]::new));
			return Maps.entryOfNullables(method.getReturnType(), result);
		} catch(final IllegalAccessException illegalAccessException) {
			throw new FlangeCloudException("Unable to access class `%s` method `%s`.".formatted(getService().getClass().getName(), methodName),
					illegalAccessException);
		} catch(final IllegalArgumentException illegalArgumentException) {
			throw new FlangeCloudException(
					"Inappropriate call to class `%s` method `%s`: %s.".formatted(getService().getClass().getName(), methodName, illegalArgumentException.getMessage()),
					illegalArgumentException);
		}
	}

	/**
	 * Unmarshals a list of arguments, already parsed into JSON default types, based upon actual argument types.
	 * @implSpec This implementation calls {@link Marshalling#convertValue(Object, Type)}.
	 * @param marshalledArgs The arguments parsed into a map of general objects, lists, and numbers.
	 * @param argTypes The types of arguments as provided by a method, potentially each including generics information, such as those supplied by
	 *          {@link Method#getGenericParameterTypes()}.
	 * @return A list of unmarshalled argument values to be passed to a method with the given argument types.
	 * @throws IllegalArgumentException if one of the marshalled arguments cannot be converted.
	 */
	static List<?> unmarshalMethodArgs(@Nonnull final List<?> marshalledArgs, @Nonnull final List<Type> argTypes) {
		return zip(marshalledArgs.stream(), argTypes.stream(), (marshalledArg, argType) -> convertValue(marshalledArg, argType)).toList();
	}

	static Stream<Method> declaredMethodsHavingName(@Nonnull final Class<?> clazz, @Nonnull final String methodName) { //TODO move to PLOOP
		requireNonNull(methodName);
		return declaredMethods(clazz).filter(method -> method.getName().equals(methodName));
	}

	static Stream<Method> declaredMethods(@Nonnull final Class<?> clazz) { //TODO move to PLOOP
		return Stream.of(clazz.getDeclaredMethods());
	}

}
