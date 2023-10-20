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

import java.io.*;
import java.lang.reflect.*;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.*;

import com.fasterxml.classmate.GenericType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.exc.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.globalmentor.util.DataException;

/**
 * Shared definitions and common utilities for marshalling in AWS.
 * @apiNote While the American spelling is apparently "marshaling", the British spelling "marshalling" is more consistent with other doubled consonants when
 *          adding endings, and removes confusion over whether the second "a" is long or short. Furthermore
 *          <a href="https://trends.google.com/trends/explore?q=marshalling,marshaling">trends for the two words</a> seem to favor "marshalling". See also
 *          <a href="https://stackoverflow.com/q/4434590"><cite>"Marshall" or "Marshal"? "Marshalling" or "Marshaling"?</cite></a>.
 * @author Garret Wilson
 */
public class Marshalling {

	/** Original object mapper for internal conversions. */
	private static final ObjectMapper OBJECT_MAPPER;

	/** Reader for JSON deserialization. */
	public static final ObjectReader JSON_READER;

	/** Writer for JSON serialization. */
	public static final ObjectWriter JSON_WRITER;

	/** The invocation parameter indicating the method name being invoked. */
	public static final String PARAM_FLANGE_METHOD_NAME = "flange-methodName";

	/** The invocation parameter indicating the arguments of the method being invoked. */
	public static final String PARAM_FLANGE_METHOD_ARGS = "flange-methodArgs";

	/**
	 * Factory for creating an appropriately configured Jackson object mapper.
	 * @return A new instance of an object mapper, correctly configured for marshalling.
	 */
	private static ObjectMapper createJsonObjectMapper() {
		return JsonMapper.builder().serializationInclusion(JsonInclude.Include.NON_ABSENT) //
				.addModule(new Jdk8Module()) //
				.addModule(new SimpleModule() //
						.addSerializer(Locale.class, new JsonSerializer<Locale>() { //TODO switch to using module from JAVA-322 when implemented
							@Override
							public boolean isEmpty(SerializerProvider provider, Locale value) {
								return value == null || value.equals(Locale.ROOT);
							};

							@Override
							public void serialize(Locale value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
								gen.writeString(value.toLanguageTag());
							}
						}) //
						.addKeySerializer(Locale.class, new JsonSerializer<Locale>() { //TODO switch to using module from JAVA-322 when implemented
							@Override
							public boolean isEmpty(SerializerProvider provider, Locale value) {
								return value == null || value.equals(Locale.ROOT);
							};

							@Override
							public void serialize(Locale value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
								gen.writeFieldName(value.toLanguageTag());
							}
						}))
				.build();
	}

	static {
		OBJECT_MAPPER = createJsonObjectMapper();
		JSON_READER = OBJECT_MAPPER.reader();
		JSON_WRITER = OBJECT_MAPPER.writer();
	}

	/**
	 * Converts a value (which may be a node in a map of already parsed JSON) to the indicated type.
	 * @param <T> The expected type.
	 * @param fromValue The existing value.
	 * @param toValueType The destination type, which may be a {@link Class} or even some other type that provides further generics information, such as
	 *          {@link ParameterizedType}.
	 * @return The converted value.
	 * @throws IllegalArgumentException if the argument cannot be converted.
	 */
	public static <T> T convertValue(final Object fromValue, final Type toValueType) throws IllegalArgumentException {
		return OBJECT_MAPPER.convertValue(fromValue, OBJECT_MAPPER.getTypeFactory().constructType(toValueType));
	}

	//	public static <T> T convertValue(Object fromValue, Class<T> toValueType)

	/**
	 * Provides a reader configured to data bind into a specified generic type.
	 * @param genericType The ClassMate generic type holder.
	 * @return A new reader for the specified generic type.
	 * @throws IllegalArgumentException if the given type is not a direct subclass of {@link GenericType} providing a single generic type parameter.
	 */
	public static ObjectReader jsonReaderForType(@Nonnull final GenericType<?> genericType) {
		return JSON_READER.forType(typeTokenToJavaType(JSON_READER.getConfig().getTypeFactory(), genericType));
	}

	/**
	 * Provides a reader configured to data bind into a specified type.
	 * @apiNote This method replaces a {@link ObjectReader#withType(Type)} which has been deprecated with no replacement; see
	 *          <a href="https://github.com/FasterXML/jackson-databind/issues/4153"><code>jacksond-databind</code> #4153</a>.
	 * @param type The type, possibly with generic information.
	 * @return A new reader for the specified type.
	 */
	public static ObjectReader jsonReaderForType(@Nonnull final Type type) {
		return JSON_READER.forType(JSON_READER.getConfig().getTypeFactory().constructType(type));
	}

	/**
	 * Converts a ClassMate "generic type" to a Jackson "Java type" using the supplied type factory.
	 * @param typeFactory The type factory for creating Jackson types.
	 * @param typeToken The type token: either a {@link Class}; a super type token such as {@code com.fasterxml.classmate.GenericType<T>}, Spring
	 *          {@code org.springframework.core.ParameterizedTypeReference<T>}, Guava {@code com.google.common.reflect.TypeToken<T>}; or some other {@link Type}.
	 * @return A Jackson Java type token instance.
	 * @see <a href="https://github.com/FasterXML/java-classmate/issues/69">Convert GenericType or ResolvedType to JavaType #69</a>
	 * @see <a href="https://gafter.blogspot.com/2006/12/super-type-tokens.html">Super Type Tokens</a>
	 * @throws IllegalArgumentException if the given object is not a {@link Class}; or a direct subclass of a class providing a single generic type parameter.
	 */
	public static JavaType typeTokenToJavaType(@Nonnull final TypeFactory typeFactory, @Nonnull final Object typeToken) {
		return typeFactory.constructType(typeTokenToType(typeToken));
	}

	/**
	 * Returns a type represented by a type token: either a {@link Class}; or a direct subclass of some parameterized type (a <dfn>super type token</dfn>), where
	 * the super class parameterized type represents the type; some other {@link Type}. If the given type token is a {@link Type} that is not a super type token,
	 * the object itself will be returned.
	 * @param typeToken The type token: either a {@link Class}; or a super type token such as {@code com.fasterxml.classmate.GenericType<T>}, Spring
	 *          {@code org.springframework.core.ParameterizedTypeReference<T>}, Guava {@code com.google.common.reflect.TypeToken<T>}; or some other {@link Type}.
	 * @return The type represented by the type token.
	 * @see <a href="https://gafter.blogspot.com/2006/12/super-type-tokens.html">Super Type Tokens</a>
	 * @throws IllegalArgumentException if the given object is not a {@link Class}; or a direct subclass of a class providing a single generic type parameter.
	 */
	public static Type typeTokenToType(@Nonnull final Object typeToken) { //TODO move to PLOOP
		//super type token (check first, because ClassMate `GenericType<T>` is also a `Type`)
		final Type superTypeTokenSuperClass = typeToken.getClass().getGenericSuperclass();
		if(superTypeTokenSuperClass instanceof ParameterizedType parameterizedType) {
			final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
			if(actualTypeArguments.length == 1) {
				return actualTypeArguments[0];
			}
		}
		//type
		if(typeToken instanceof Type) { //types in general, if they are not super type tokens needing "unwrapping", can be returned directly  
			return (Type)typeToken;
		}
		throw new IllegalArgumentException("Type token must be an instance of `Class`, or have a super class providing a single generic type argument.");
	}

	/**
	 * Serializes some value for marshalling.
	 * @apiNote The returned output stream is useful for unit testing.
	 * @implNote If an {@code Optional<>} instance is given, its value will be extracted and marshalled, using <code>null</code> for
	 *           <code>Optional.empty()</code>.
	 * @param <OS> The type of output stream to write to.
	 * @param value The value to marshal.
	 * @param outputStream The output stream to which to write the value.
	 * @return The given output stream.
	 * @throws IOException If an I/O error occurs.
	 * @throws DataException If a data conversion or mapping exception occurs.
	 */
	public static <OS extends OutputStream> OS marshalJson(@Nullable final Object value, OS outputStream) throws IOException, DataException {
		try {
			JSON_WRITER.writeValue(outputStream, value);
		} catch(final StreamWriteException | DatabindException jacksonException) {
			throw new DataException(jacksonException);
		}
		return outputStream;
	}

	/**
	 * Deserializes some marshalled value.
	 * @implNote {@link Optional} is supported, both with a value present and not present.
	 * @param <T> The type of value expected.
	 * @param inputStream The input stream from which to read the value.
	 * @param valueType The type information for the expected type, including generics information if appropriate.
	 * @return The the unmarshalled value, which may be {@link Optional#empty()} if the input is JSON <code>null</code> and {@link Optional} is expected;
	 *         <code>null</code> if the input is JSON <code>null</code> for any expected type, and <code>null</code> if the value is any valid JSON and the
	 *         expected value is {@link Void}.
	 * @throws IOException If an I/O error occurs.
	 * @throws DataException If a data conversion or mapping exception occurs.
	 */
	public static <T> T unmarshalJson(@Nonnull final InputStream inputStream, @Nonnull GenericType<T> valueType) throws IOException, DataException {
		try {
			return jsonReaderForType(valueType).readValue(inputStream);
		} catch(final StreamReadException | DatabindException jacksonException) {
			throw new DataException(jacksonException);
		}
	}

}
