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

import static java.net.HttpURLConnection.*;
import static java.util.Collections.*;
import static java.util.Objects.*;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.*;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.globalmentor.java.StackTrace;

import dev.flange.cloud.*;
import io.clogr.Clogged;

/**
 * Definitions and utilities for working with <a href="https://aws.amazon.com/lambda/">AWS Lambda</a>.
 * @author Garret Wilson
 */
public final class AwsLambda {

	/**
	 * The HTTP status code of <code>200</code> for the <code>RequestResponse</code> invocation type.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse#statusCode()
	 */
	public static final int STATUS_CODE_REQUEST_RESPONSE_SUCCESS = HTTP_OK;

	/**
	 * The HTTP status code of <code>202</code> for the <code>Event</code> invocation type.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse#statusCode()
	 */
	public static final int STATUS_CODE_EVENT_SUCCESS = HTTP_ACCEPTED;

	/**
	 * The HTTP status code of <code>204</code> for the <code>DryRun</code> invocation type.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse#statusCode()
	 */
	public static final int STATUS_CODE_DRY_RUN_SUCCESS = HTTP_NO_CONTENT;

	/**
	 * The identifier for an unhandled invoke response function error.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse#functionError()
	 */
	public static final String FUNCTION_ERROR_UNHANDLED = "Unhandled";

	/**
	 * Deserializes an AWS Lambda unhandled error marshalled as JSON, typically in the payload of a lambda invocation response.
	 * @param inputStream The input stream from which to read the value.
	 * @return The the unmarshalled unhandled error.
	 * @throws IOException If an I/O error occurs.
	 * @throws FlangeMarshalException if a data conversion or mapping exception occurs.
	 */
	public static UnhandledError unmarshalUnhandledError(@Nonnull final InputStream inputStream) throws IOException, FlangeMarshalException {
		try {
			return Marshalling.jsonReaderForType(UnhandledError.class).readValue(inputStream);
		} catch(final StreamReadException | DatabindException jacksonException) {
			throw new FlangeMarshalException("Error unmarshalling value.", jacksonException);
		}
	}

	/**
	 * Represents an AWS Lambda unhandled error as marshalled in the response body when the invoke response indicates {@value AwsLambda#FUNCTION_ERROR_UNHANDLED}.
	 * @see <a href="https://docs.aws.amazon.com/lambda/latest/dg/java-exceptions.html">AWS Lambda function errors in Java</a>
	 */
	public record UnhandledError(@Nonnull String errorType, @Nullable String errorMessage, @Nonnull List<String> stackTrace, @Nullable UnhandledError cause)
			implements Clogged {

		/**
		 * Validating constructor.
		 * @implSpec Ensures that the error type is not <code>null</code>, and converts any <code>null</code> stack trace to an empty list (e.g. for a missing stack
		 *           trace in the serialization).
		 * @param errorType The name of the class of the type of error.
		 * @param errorMessage The error message.
		 * @param stackTrace The string forms of the stack trace elements.
		 * @param cause The unhandled error cause, or <code>null</code> if not present.
		 */
		public UnhandledError {
			requireNonNull(errorType);
			if(stackTrace == null) {
				stackTrace = emptyList();
			}
		}

		/**
		 * Constructs an instance of this class from an existing {@link Throwable} and a series of stack trace strings. The cause and stack trace of the given
		 * throwable are ignored.
		 * @apiNote This factory method is useful for constructing an expected unhandled error instance for testing.
		 * @param throwable The throwable from which to create the unhandled error.
		 * @param stackTrace The string serializations of the stack trace.
		 * @return An instance of an unhandled error from the given information.
		 */
		static UnhandledError fromThrowable(@Nonnull final Throwable throwable, @Nonnull final String... stackTrace) {
			return fromThrowable(throwable, null, stackTrace);
		}

		/**
		 * Constructs an instance of this class from an existing {@link Throwable} and a series of stack trace strings. The cause and stack trace of the given
		 * throwable are ignored.
		 * @apiNote This factory method is useful for constructing an expected unhandled error instance for testing.
		 * @param throwable The throwable from which to create the unhandled error.
		 * @param cause The unhandled error cause, or <code>null</code> if not present.
		 * @param stackTrace The string serializations of the stack trace.
		 * @return An instance of an unhandled error from the given information.
		 */
		static UnhandledError fromThrowable(@Nonnull final Throwable throwable, @Nullable UnhandledError cause, @Nonnull final String... stackTrace) {
			return new UnhandledError(throwable.getClass().getName(), throwable.getMessage(), List.of(stackTrace), cause);
		}

		/**
		 * Constructs an instance of this class from an existing {@link Throwable} and a series of stack trace strings. The cause and stack trace of the given
		 * throwable are ignored.
		 * @apiNote This factory method is useful for constructing an expected unhandled error instance for testing. In particular this factory method uses a more
		 *          natural order for stack traces and nested causes.
		 * @param throwable The throwable from which to create the unhandled error.
		 * @param cause The unhandled error cause, or <code>null</code> if not present.
		 * @param stackTrace The string serializations of the stack trace.
		 * @return An instance of an unhandled error from the given information.
		 */
		static UnhandledError fromThrowable(@Nonnull final Throwable throwable, @Nonnull List<String> stackTrace, @Nullable UnhandledError cause) {
			return fromThrowable(throwable, cause, stackTrace.toArray(String[]::new)); //passing an array to the other factory method effectively makes a defensive copy
		}

		/**
		 * Creates an appropriate throwable to represent this unhandled error.
		 * @implSpec This implementation has special-case support for {@link UnavailableMarshalledThrowable}, allowing it to be full unmarshalled if possible to its
		 *           represented throwable.
		 * @implNote If the actual throwable indicated by {@link #errorType} cannot be instantiated for some reason (e.g. its class cannot be found or it does not
		 *           have an appropriate constructor), a placeholder exception will be returned.
		 * @return A throwable representing the information in this unhandled error.
		 */
		public Throwable toThrowable() {
			//create bare `Throwable`
			final Throwable throwable = createThrowable();
			//set stack trace
			final AtomicBoolean hasUnparseableStackTraceElement = new AtomicBoolean(false);
			final StackTraceElement[] stackTraceElements = stackTrace().stream().map(stackTraceElementString -> {
				try {
					return StackTrace.parseElement(stackTraceElementString);
				} catch(final IllegalArgumentException IllegalArgumentException) { //unparsable stack trace element
					if(hasUnparseableStackTraceElement.compareAndSet(false, true)) { //only log one warning per stack trace (if one doesn't parse, multiple elements probably won't parse)
						getLogger().atWarn().log("Unable to parse AWS Lambda marshalled stack trace element `{}`.", stackTraceElementString);
					}
					return new StackTraceElement("Unparsable", "seeLog", null, -1); //create an artificial stack trace element to represent the unparsable stack trace element
				}
			}).toArray(StackTraceElement[]::new);
			throwable.setStackTrace(stackTraceElements);
			return throwable;

		}

		/**
		 * Creates an appropriate bare throwable to represent this unhandled error. The cause will have been set, but the stack trace will not yet have been
		 * updated.
		 * @implSpec This implementation has special-case support for {@link UnavailableMarshalledThrowable}, allowing it to be full unmarshalled if possible to its
		 *           represented throwable.
		 * @implNote This implementation supports instances of {@link Throwable} with a constructor in the form <code>(String message, final Throwable cause)</code>
		 *           or <code>(String message)</code>. If the actual throwable indicated by {@link #errorType} cannot be instantiated for some reason (e.g. its
		 *           class cannot be found or it does not have an appropriate constructor), a placeholder exception will be returned.
		 * @return A bare throwable from the information in this unhandled error, which may be {@link UnavailableMarshalledThrowable} if the indicated exception
		 *         cannot be created.
		 */
		Throwable createThrowable() {
			@Nullable
			final Throwable throwableCause = cause != null ? cause.toThrowable() : null;
			try {
				String errorType = errorType(); //these may be replaced if we can unwrap an `UnavailableMarshalledThrowable` 
				String errorMessage = errorMessage();

				//Try to fully unmarshal `UnavailableMarshalledThrowable` if possible.
				//If we can extract the marshalled throwable description but that throwable can't be instantiated,
				//the code will later simple re-wrap it in an `UnavailableMarshalledThrowable`. 
				while(UnavailableMarshalledThrowable.class.getName().equals(errorType)) { //support multiple levels of unwrapping (although that's not expected to occur under normal circumstances)
					if(errorMessage == null) {
						getLogger().atWarn().log("Marshalled throwable description missing message; wrapping in another layer of placeholder throwable `{}`.",
								UnavailableMarshalledThrowable.class.getSimpleName());
					}
					try {
						final UnavailableMarshalledThrowable.MarshalledThrowableDescription marshalledThrowableDescription = UnavailableMarshalledThrowable.MarshalledThrowableDescription
								.fromMessage(errorMessage);
						errorType = marshalledThrowableDescription.marshalledClassName(); //try to instantiate the represented throwable 
						errorMessage = marshalledThrowableDescription.marshalledMessage();
					} catch(final Exception exception) { //ignore low-level throwables such as `Error`, which are not meant to be caught
						getLogger().atWarn().log(
								"Marshalled throwable description cannot be retrieved from message `{}`; wrapping in another layer of placeholder throwable `{}`.",
								errorMessage, UnavailableMarshalledThrowable.class.getSimpleName());
					}
				}

				final Class<? extends Throwable> throwableClass;
				try {
					final Class<?> classForErrorType = Class.forName(errorType);
					if(!Throwable.class.isAssignableFrom(classForErrorType)) { //we expect AWS Lambda to only give us errors that are of type `Throwable`
						getLogger().atWarn().log("Marshalled AWS Lambda error type `{}` is not a `Throwable` type; using placeholder.", errorType);
						return new UnavailableMarshalledThrowable(errorType, errorMessage, throwableCause);
					}
					@SuppressWarnings("unchecked")
					final Class<? extends Throwable> classForThrowableErrorType = (Class<? extends Throwable>)classForErrorType;
					throwableClass = classForThrowableErrorType;
				} catch(final ClassNotFoundException classNotFoundException) {
					//It's not unexpected that some classes thrown remotely might not be available on the client side;
					//just use a placeholder—no warning needed.
					return new UnavailableMarshalledThrowable(errorType, errorMessage, throwableCause);
				}

				//Try to find a constructor in the following order (assuming purposes of the parameters, as most exceptions follow this pattern):
				//* `(String message, final Throwable cause)`
				//* `(String message)` 
				try {
					final Constructor<? extends Throwable> messageCauseConstructor = throwableClass.getDeclaredConstructor(String.class, Throwable.class);
					return messageCauseConstructor.newInstance(errorMessage, throwableCause);
				} catch(final NoSuchMethodException noSuchMethodException) {
					final Constructor<? extends Throwable> messageConstructor = throwableClass.getDeclaredConstructor(String.class);
					final Throwable throwable = messageConstructor.newInstance(errorMessage);
					if(throwableCause != null) {
						throwable.initCause(throwableCause);
					}
					return throwable;
				}
			} catch(final LinkageError | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException | IllegalStateException creationException) { //`IllegalStateException` if there is a problem initializing the cause
				getLogger().atWarn().log("Marshalled AWS Lambda error type `{}` could not be created; using placeholder throwable `{}`.", errorType,
						UnavailableMarshalledThrowable.class.getSimpleName());
				return new UnavailableMarshalledThrowable(errorType, errorMessage, throwableCause);
			}
		}

	}

}
