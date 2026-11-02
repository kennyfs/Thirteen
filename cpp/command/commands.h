#ifndef COMMAND_COMMANDS_H_
#define COMMAND_COMMANDS_H_

#include <string>
#include <vector>

namespace Commands {
  int runTests(std::vector<std::string> args);
  int match(std::vector<std::string> args);
  int analyze(std::vector<std::string> args);
  int benchmark(std::vector<std::string> args);
  int exploit(std::vector<std::string> args);
  int selfplay(std::vector<std::string> args);
  int humanFeatures(std::vector<std::string> args);
  int netQ(std::vector<std::string> args);
  int evalKeys(std::vector<std::string> args);
  int record(std::vector<std::string> args);
  int gui(std::vector<std::string> args);
}

#endif // COMMAND_COMMANDS_H_
