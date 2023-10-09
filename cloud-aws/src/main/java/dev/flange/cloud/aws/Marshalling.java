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

import java.io.IOException;
import java.lang.reflect.*;
import java.util.Locale;

import javax.annotation.Nonnull;

import com.fasterxml.classmate.GenericType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/**
 * Shared definitions and common utilities for marshalling in AWS.
 * @apiNote While the American spelling is apparently "marshaling", the British spelling "marshalling" is more consistent with other doubled consonants when
 *          adding endings, and removes confusion over whether the second "a" is long or short. Furthermore
 *          <a href="https://trends.google.com/trends/explore?q=marshalling,marshaling">trends for the two words</a> seem to favor "marshalling". See also
 *          <a href="https://stackoverflow.com/q/4434590"><cite>"Marshall" or "Marshal"? "Marshalling" or "Marshaling"?</cite></a>.
 * @author Garret Wilson
 */
public class Marshalling {

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
		final ObjectMapper objectMapper = createJsonObjectMapper();
		JSON_READER = objectMapper.reader();
		JSON_WRITER = objectMapper.writer();
	}

	/**
	 * Provides a reader configured to data bind into a specified type.
	 * @param genericType The ClassMate generic type holder.
	 * @return A new reader for the specified generic type.
	 * @throws IllegalArgumentException if the given type is not a direct subclass of {@link GenericType} providing a single generic type parameter.
	 */
	public static ObjectReader jsonReaderForType(@Nonnull final GenericType<?> genericType) {
		return JSON_READER.forType(toJavaType(JSON_READER.getConfig().getTypeFactory(), genericType));
	}

	/**
	 * Converts a ClassMate "generic type" to a Jackson "Java type" using the supplied type factory.
	 * @param typeFactory The type factory for creating Jackson types.
	 * @param genericType The ClassMate generic type holder.
	 * @return A Jackson Java type token instance.
	 * @see <a href="https://github.com/FasterXML/java-classmate/issues/69">Convert GenericType or ResolvedType to JavaType #69</a>
	 * @throws IllegalArgumentException if the given type is not a direct subclass of {@link GenericType} providing a single generic type parameter.
	 */
	public static JavaType toJavaType(@Nonnull final TypeFactory typeFactory, @Nonnull final GenericType<?> genericType) {
		final Type genericTypeSubclassSuperClass = genericType.getClass().getGenericSuperclass();
		if(genericTypeSubclassSuperClass instanceof ParameterizedType parameterizedType) {
			checkArgument(GenericType.class.equals(parameterizedType.getRawType()), "Type token must be immediate subclass of `%s`.",
					GenericType.class.getSimpleName());
			final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
			if(actualTypeArguments.length == 1) {
				return typeFactory.constructType(actualTypeArguments[0]);
			}
		}
		throw new IllegalArgumentException("Type token must provide a single generic type argument.");
	}

}
