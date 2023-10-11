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

import dev.flange.*;
import io.clogr.Clogged;

/**
 * AWS Lambda handler for a FaaS service.
 * @param <S> The type of backing service implementation.
 * @author Garret Wilson
 */
public class AwsCloudFunctionServiceHandler<S> implements RequestStreamHandler, Flanged, Clogged {

	private final S service;

	/** @return The backing service implementation. */
	protected S getService() {
		return service;
	}

	//TODO consider passing both the interface and the implementation

	/**
	 * FaaS service class constructor.
	 * @implSpec The service dependency will be looked up and instantiated using Flange.
	 * @param serviceClass The class representing the type of service implementation to ultimately service FaaS requests.
	 * @throws MissingDependencyException if an appropriate dependency of the requested type could not be found.
	 * @throws DependencyException if there is some general error retrieving or creating the dependency.
	 */
	protected AwsCloudFunctionServiceHandler(@Nonnull Class<S> serviceClass) {
		this.service = getDependencyInstanceByType(serviceClass);
	}

	@Override
	public void handleRequest(final InputStream inputStream, final OutputStream outputStream, final Context context) throws IOException {
		getLogger().info("Handling request for backing service of type `{}`.", getService().getClass().getName()); //TODO delete; testing

		@SuppressWarnings("unchecked")
		final Map<String, Object> inputs = JSON_READER.readValue(new BufferedInputStream(inputStream), Map.class);
		final String methodName = Optional.ofNullable(inputs.get(PARAM_FLANGE_METHOD_NAME)).flatMap(asInstance(String.class))
				.orElseThrow(() -> new IllegalArgumentException("Missing input `%s` method name string.".formatted(PARAM_FLANGE_METHOD_NAME)));
		final Class<?> serviceClass = getService().getClass();
		final Method method;
		try {
			method = declaredMethodsHavingName(serviceClass, methodName).collect(toOnly());
		} catch(final NoSuchElementException noSuchElementException) {
			throw new IllegalArgumentException("No service `%s` method named `%s` found.".formatted(serviceClass.getName(), methodName));
		} catch(final IllegalArgumentException illegalArgumentException) {
			throw new IllegalArgumentException(
					"Service `%s` has multiple methods named `%s`; currently only one is supported.".formatted(serviceClass.getName(), methodName));
		}
		//TODO delete getLogger().atInfo().log("Handling request for method name `{}`.", methodName); //TODO delete
		final Class<?> methodReturnType = method.getReturnType();
		checkArgument(methodReturnType.equals(Future.class) || methodReturnType.equals(CompletableFuture.class),
				"Class `%s` method `%s` expected to have a `Future<?>` or `CompletableFuture<?>` return type; found `%s`.", serviceClass.getName(), methodName,
				methodReturnType.getName());
		final List<Type> methodArgTypes = asList(method.getGenericParameterTypes()); //TODO add PLOOP convenience list method
		final List<?> marshalledMethodArgs = Optional.ofNullable(inputs.get(PARAM_FLANGE_METHOD_ARGS)).flatMap(asInstance(List.class))
				.orElseThrow(() -> new IllegalArgumentException("Missing input `%s` method arguments list.".formatted(PARAM_FLANGE_METHOD_ARGS)));
		final List<?> methodArgs = unmarshalMethodArgs(marshalledMethodArgs, methodArgTypes);
		final Object output;
		try {
			final Future<?> result;
			try {
				result = (Future<?>)method.invoke(getService(), methodArgs.toArray(Object[]::new));
			} catch(final InvocationTargetException invocationTargetException) {
				final Throwable cause = invocationTargetException.getCause();
				//TODO use Java 21 pattern matching
				if(cause instanceof IOException ioException) { //decide if we want to throw or wrap IOException; should it be distinguished from an Lambda IOException?
					throw ioException;
				} else if(cause instanceof RuntimeException runtimeException) {
					throw runtimeException;
				}
				throw new IllegalStateException("Unrecognized exception type `%s`.".formatted(cause.getClass().getName())); //TODO improve
			} catch(final IllegalAccessException illegalAccessException) {
				throw new IllegalStateException("Unable to access class `%s` method `%s`.".formatted(serviceClass.getName(), methodName)); //TODO improve
			} catch(final IllegalArgumentException illegalArgumentException) {
				throw illegalArgumentException; //TODO determine best approach
			}
			output = result.get(); //TODO use timeout with `get(long timeout, TimeUnit unit)` 
		} catch(final CancellationException cancellationException) {
			throw new RuntimeException("Service operation cancelled while invoking class `%s` method `%s`.".formatted(serviceClass.getName(), methodName)); //TODO consider retrying on the client side
		} catch(final InterruptedException interruptedException) {
			throw new RuntimeException("Interrupted while invoking class `%s` method `%s`.".formatted(serviceClass.getName(), methodName)); //TODO consider retrying on the client side
		} catch(final ExecutionException executionException) {
			//TODO check for timeout exception and retry, etc.; actually, don't retry here—retry on the client side
			final Throwable cause = executionException.getCause();
			//TODO use Java 21 pattern matching
			if(cause instanceof IOException ioException) { //decide if we want to throw or wrap IOException; should it be distinguished from an Lambda IOException?
				throw ioException;
			} else if(cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new IllegalStateException("Unrecognized exception type `%s`.".formatted(cause.getClass().getName())); //TODO improve
		}
		//final ClientContext clientContext = context.getClientContext();	//TODO see if this can provide us information from https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/lambda/model/InvokeRequest.Builder.html#clientContext(java.lang.String)
		final BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
		JSON_WRITER.writeValue(bufferedOutputStream, output); //Jackson, when writing `Optional<>`, will extract the value automatically, serializing `null` for `Optional.empty()`
		bufferedOutputStream.flush();
	}

	/**
	 * Unmarshals a list of arguments, already parsed into JSON default types, based upon actual argument types.
	 * @implSpec This implementation calls {@link Marshalling#convertValue(Object, Type)}.
	 * @param marshalledArgs The arguments parsed into a map of general objects, lists, and numbers.
	 * @param argTypes The types of arguments as provided by a method, potentially each including generics information, such as those supplied by
	 *          {@link Method#getGenericParameterTypes()}.
	 * @return A list of unmarshalled argument values to be passed to a method with the given argument types.
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
