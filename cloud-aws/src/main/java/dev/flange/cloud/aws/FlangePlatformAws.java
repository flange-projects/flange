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

/**
 * Definitions and utilities for the AWS platform.
 * @author Garret Wilson
 * @see <a href="https://aws.amazon.com/">AWS</a>
 */
public class FlangePlatformAws {

	/**
	 * The configuration key for the Flange AWS platform profile identifier.
	 * @apiNote The AWS <a href=
	 *          "https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/auth/credentials/ProfileCredentialsProvider.html"><code>ProfileCredentialsProvider</code></a>
	 *          detects the profile either as the <code>AWS_PROFILE</code> environment variable or the <code>aws.profile</code> Java system property. This
	 *          definition as a configuration key can be applied to either using a configuration library such as Confound.
	 * @see <a href="https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html#cli-configure-files-using-profiles">AWS Command Line Interface §
	 *      Configuration and credential file settings: Using named profiles</a>
	 * @see <a href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-profiles.html">AWS SDK for Java 2.x § Use profiles</a>
	 */
	public static final String CONFIG_KEY_FLANGE_AWS_PROFILE = "aws.profile";

}
