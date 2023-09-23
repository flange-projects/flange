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

import javax.annotation.*;

import com.amazonaws.services.lambda.runtime.*;

import dev.flange.*;
import io.clogr.Clogged;

/**
 * AWS Lambda handler for a FaaS service.
 * @param <S> The type of backing service implementation.
 * @author Garret Wilson
 */
public class FaasServiceLambdaHandler<S> implements RequestStreamHandler, Flanged, Clogged {

	private final S service;

	/** @return The backing service implementation. */
	protected S getService() {
		return service;
	}

	/**
	 * FaaS service class constructor.
	 * @implSpec The service dependency will be looked up and instantiated using Flange.
	 * @param serviceClass The class representing the type of service implementation to ultimately service FaaS requests.
	 * @throws MissingDependencyException if an appropriate dependency of the requested type could not be found.
	 * @throws DependencyException if there is some general error retrieving or creating the dependency.
	 */
	protected FaasServiceLambdaHandler(@Nonnull Class<S> serviceClass) {
		this.service = getDependencyInstanceByType(serviceClass);
	}

	@Override
	public void handleRequest(final InputStream inputStream, final OutputStream outputStream, final Context context) throws IOException {
		getLogger().info("Handling request for backing service of type `{}`.", getService().getClass().getName()); //TODO delete; testing
	}

}
