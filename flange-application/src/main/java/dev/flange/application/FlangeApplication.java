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

package dev.flange.application;

import static dev.flange.Flange.*;

import dev.flange.*;
import io.clogr.*;

/**
 * Convenience interface for a Flange application with CLI parameter configuration.
 * <p>
 * Subclasses should override {@link #run()} for program logic, but call {@link #start()} to actually begin execution of the application.
 * </p>
 * @author Garret Wilson
 */
public interface FlangeApplication extends Runnable, Clogged, Flanged {

	/** The CLI parameter to set the Flange environment identifier. */
	public static final String CLI_PARAM_FLANGE_ENV = "--flange-env";

	/** The CLI parameter to set the Flange platform identifier. */
	public static final String CLI_PARAM_FLANGE_PLATFORM = "--flange-platform";

	/**
	 * Starts the application
	 * @implSpec The default implementation calls {@link #run()} and returns <code>0</code> as an exit code of "OK".
	 * @return The application status.
	 */
	public default int start() {
		run();
		return 0;
	}

	/**
	 * Configures Flange from application <code>main(…)</code> arguments.
	 * @implSpec This implementation supports the following CLI options:
	 *           <dl>
	 *           <dt>{@value #CLI_PARAM_FLANGE_ENV}</dt>
	 *           <dd>Sets the system property {@value Flange#CONFIG_KEY_FLANGE_ENV}.</dd>
	 *           <dt>{@value #CLI_PARAM_FLANGE_PLATFORM}</dt>
	 *           <dd>Sets the system property {@value Flange#CONFIG_KEY_FLANGE_PLATFORM}.</dd>
	 *           </dl>
	 * @param args application arguments.
	 */
	public static void configureFlangeFromArgs(final String[] args) {
		for(int i = 0; i < args.length - 1; i++) { //TODO detect configuration in `flange-config.*` as well
			switch(args[i]) {
				case CLI_PARAM_FLANGE_ENV -> {
					final String flangeEnv = args[i + 1];
					System.setProperty(CONFIG_KEY_FLANGE_ENV, flangeEnv);
					Clogr.getLogger(FlangeApplication.class).atDebug().log("Using Flange environment: `{}`", flangeEnv);
				}
				case CLI_PARAM_FLANGE_PLATFORM -> {
					final String flangePlatform = args[i + 1];
					System.setProperty(CONFIG_KEY_FLANGE_PLATFORM, flangePlatform);
					Clogr.getLogger(FlangeApplication.class).atDebug().log("Using Flange platform: `{}`", flangePlatform);
				}
			}
		}
	}

}
