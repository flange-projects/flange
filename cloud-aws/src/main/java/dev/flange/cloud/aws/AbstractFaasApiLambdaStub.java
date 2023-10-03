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

import java.util.concurrent.CompletableFuture;

import javax.annotation.*;

import com.fasterxml.classmate.*;

import io.clogr.Clogged;

/**
 * Abstract base class for implementing an AWS Lambda stub for a FaaS API.
 * @author Garret Wilson
 */
public abstract class AbstractFaasApiLambdaStub implements Clogged {

	private final TypeResolver typeResolver = new TypeResolver();

	/** @return The object for resolving generic type information. */
	protected TypeResolver getTypeResolver() {
		return typeResolver;
	}

	/**
	 * Invokes a cloud function asynchronously, returning a completable future future for the result.
	 * @param <T> The type of value to be returned by the future.
	 * @param genericReturnType Generics-aware information about the return type of the future.
	 * @param arguments The arguments of the cloud function to be marshaled.
	 * @return The future return value.
	 */
	protected <T> CompletableFuture<T> invokeAsync(@Nonnull GenericType<T> genericReturnType, @Nonnull final Object... arguments) {
		final ResolvedType resolvedReturnType = getTypeResolver().resolve(genericReturnType);
		if(resolvedReturnType.getErasedType().equals(String.class)) {
			@SuppressWarnings("unchecked")
			final CompletableFuture<T> result = (CompletableFuture<T>)CompletableFuture.completedFuture("called invokeAsync()");
			return result;
		}
		throw new IllegalArgumentException("Unsupported cloud function return type `%s`.".formatted(resolvedReturnType));
	}

}
