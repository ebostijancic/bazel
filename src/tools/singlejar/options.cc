// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "src/tools/singlejar/options.h"

#include "src/tools/singlejar/diag.h"
#include "src/tools/singlejar/token_stream.h"

void Options::ParseCommandLine(int argc, const char *argv[]) {
  ArgTokenStream tokens(argc, argv);
  std::string optarg;
  while (!tokens.AtEnd()) {
    if (tokens.MatchAndSet("--output", &output_jar) ||
        tokens.MatchAndSet("--main_class", &main_class) ||
        tokens.MatchAndSet("--java_launcher", &java_launcher) ||
        tokens.MatchAndSet("--deploy_manifest_lines", &manifest_lines) ||
        tokens.MatchAndSet("--sources", &input_jars) ||
        tokens.MatchAndSet("--resources", &resources) ||
        tokens.MatchAndSet("--classpath_resources", &classpath_resources) ||
        tokens.MatchAndSet("--include_prefixes", &include_prefixes) ||
        tokens.MatchAndSet("--exclude_build_data", &exclude_build_data) ||
        tokens.MatchAndSet("--compression", &force_compression) ||
        tokens.MatchAndSet("--dont_change_compression",
                           &preserve_compression) ||
        tokens.MatchAndSet("--normalize", &normalize_timestamps) ||
        tokens.MatchAndSet("--no_duplicates", &no_duplicates) ||
        tokens.MatchAndSet("--verbose", &verbose) ||
        tokens.MatchAndSet("--warn_duplicate_resources",
                           &warn_duplicate_resources)) {
      continue;
    } else if (tokens.MatchAndSet("--build_info_file", &optarg)) {
      build_info_files.push_back(optarg);
      continue;
    } else if (tokens.MatchAndSet("--extra_build_info", &optarg)) {
      build_info_lines.push_back(optarg);
      continue;
    } else {
      diag_errx(1, "Bad command line argument %s", tokens.token().c_str());
    }
  }

  if (output_jar.empty()) {
    diag_errx(1, "Use --output <output_jar> to specify the output file name");
  }
  if (force_compression && preserve_compression) {
    diag_errx(
        1,
        "--compression and --dont_change_compression are mutually exclusive");
  }
}
