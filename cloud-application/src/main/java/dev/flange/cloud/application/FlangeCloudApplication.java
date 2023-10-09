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

package dev.flange.cloud.application;

import dev.flange.application.FlangeApplication;
import dev.flange.cloud.aws.FlangePlatformAws;
import io.clogr.Clogr;

/**
 * Convenience interface for a Flange Cloud application with CLI parameter configuration.
 * <p>
 * Subclasses should override {@link #run()} for program logic, but call {@link #start()} to actually begin execution of the application.
 * </p>
 * @author Garret Wilson
 */
public interface FlangeCloudApplication extends FlangeApplication {

	/**
	 * The CLI parameter to set the Flange AWS platform profile identifier.
	 * @see <a href="https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html#cli-configure-files-using-profiles">AWS Command Line Interface §
	 *      Configuration and credential file settings: Using named profiles</a>
	 */
	public static final String CLI_PARAM_FLANGE_AWS_PROFILE = "--flange-aws-profile"; //TODO move to a pluggable platform module

	/**
	 * Configures Flange Cloud from application <code>main(…)</code> arguments.
	 * @implSpec This implementation performs the default Flange application configuration
	 * @implSpec This implementation supports the following CLI options:
	 *           <dl>
	 *           <dt>{@value #CLI_PARAM_FLANGE_AWS_PROFILE}</dt>
	 *           <dd>Sets the system property {@value FlangePlatformAws#CONFIG_KEY_FLANGE_AWS_PROFILE}.</dd>
	 *           </dl>
	 * @param args application arguments.
	 */
	public static void configureFlangeFromArgs(final String[] args) {
		FlangeApplication.configureFlangeFromArgs(args); //perform default Flange application configuration
		for(int i = 0; i < args.length - 1; i++) { //TODO detect configuration in `flange-config.*` as well
			switch(args[i]) {
				case CLI_PARAM_FLANGE_AWS_PROFILE -> {
					final String awsProfile = args[i + 1];
					System.setProperty(FlangePlatformAws.CONFIG_KEY_FLANGE_AWS_PROFILE, awsProfile);
					Clogr.getLogger(FlangeApplication.class).atDebug().log("Using AWS profile `{}`.", awsProfile);
				}
			}
		}
	}

}
