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

package dev.flange.example.cloud.faas.app;

import static java.util.Objects.*;
import static java.util.concurrent.TimeUnit.*;

import java.util.concurrent.*;

import javax.annotation.*;

import dev.flange.Flange;
import dev.flange.example.cloud.faas.service.api.MessageService;

/**
 * Example application showing usage of a Flange Function-as-a-Service (FaaS).
 * @author Garret Wilson
 */
public class ExampleFaasApp implements Runnable {

	private final MessageService messageService;

	/**
	 * Message service constructor
	 * @param messageService The message service to use.
	 */
	public ExampleFaasApp(@Nonnull final MessageService messageService) {
		this.messageService = requireNonNull(messageService);
	}

	@Override
	public void run() {
		final CompletableFuture<String> futureGreeting = messageService.getGreeting().completeOnTimeout("Hi (timeout)", 5, SECONDS);
		final CompletableFuture<String> futureName = messageService.getName().completeOnTimeout("Everybody (timeout)", 5, SECONDS);

		final String message;
		try {
			message = futureGreeting.thenCombine(futureName, (greeting, name) -> "%s, %s!".formatted(greeting, name)).get();
		} catch(final InterruptedException interruptedException) {
			throw new RuntimeException(interruptedException);
		} catch(final ExecutionException exceptionExecutionException) {
			final Throwable cause = exceptionExecutionException;
			System.err.println("Error accessing message service: %s".formatted(cause.getMessage()));
			throw cause instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(cause);
		}

		System.out.println(message);
	}

	/**
	 * Main application entry point.
	 * @param args application arguments.
	 */
	public static void main(final String[] args) {
		final MessageService messageService = Flange.getDependencyConcern().getDependencyInstanceByType(MessageService.class);
		final ExampleFaasApp app = new ExampleFaasApp(messageService);
		app.run();
	}

}
