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

package dev.flange.example.cloud.faas.service.impl;

import static java.util.concurrent.CompletableFuture.*;

import java.util.concurrent.CompletableFuture;

import dev.flange.cloud.FaasService;
import dev.flange.example.cloud.faas.service.api.MessageService;

/**
 * Implementation of a service for producing messages.
 * @author Garret Wilson
 */
@FaasService
public class MessageServiceImpl implements MessageService {

	@Override
	public CompletableFuture<String> getGreeting() {
		return completedFuture("Hello");
	}

	@Override
	public CompletableFuture<String> getName() {
		return completedFuture("World");
	}

}
