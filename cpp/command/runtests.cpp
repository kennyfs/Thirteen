#include "../command/commands.h"
#include "../tests/tests.h"

using namespace std;

int Commands::runTests(vector<string> args) {
  (void)args;
  Tests::runAll();
  return 0;
}
