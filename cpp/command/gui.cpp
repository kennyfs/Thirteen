#include "../command/commands.h"
#include "../command/guiengine.h"

#include <iostream>

using namespace std;

// The engine of python/play_gui.py: one JSON request per line on stdin, one JSON answer per line on stdout. See
// command/guiengine.h for the requests.
int Commands::gui(vector<string> args) {
  (void)args;
  std::ios::sync_with_stdio(false);
  GuiEngine engine;
  string line;
  while(getline(cin, line)) {
    if(Global::trim(line).empty())
      continue;
    cout << engine.handleLine(line) << endl;
  }
  return 0;
}
