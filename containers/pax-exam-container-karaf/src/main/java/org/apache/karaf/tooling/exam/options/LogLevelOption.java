/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.tooling.exam.options;

import org.ops4j.pax.exam.Option;

/**
 * While the log-level could also be configured using the config options in the configuration files we also provide a
 * more easy option here.
 */
public class LogLevelOption implements Option {

    public static enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    private LogLevel logLevel;

    public LogLevelOption() {
    }

    public LogLevelOption(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public LogLevelOption logLevel(LogLevel _logLevel) {
        this.logLevel = _logLevel;
        return this;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

}
