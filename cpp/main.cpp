#include "command/commands.h"
#include "core/global.h"

#include <iostream>

using namespace std;

static void printHelp(const string& program) {
  cout << "Usage: " << program << " SUBCOMMAND [ARGS]" << endl;
  cout << endl;
  cout << "Subcommands:" << endl;
  cout << "  runtests   Run the unit tests." << endl;
  cout << "  match      Play duplicate deals between players and report their average results." << endl;
  cout << "  analyze    Evaluate the arrangements of one hand by Monte Carlo." << endl;
  cout << "  exploit    Measure how much a best response gains against a player." << endl;
  cout << "  benchmark  Time the building blocks." << endl;
  cout << "  selfplay   Generate training data for the network." << endl;
  cout << "  humanfeatures  Turn recorded human arrangements into features for the human model." << endl;
  cout << "  netq       Print a model's Q for the candidates of a hand (for python/check_onnx.py)." << endl;
  cout << "  evalkeys   Print the keys of 3- or 5-card hands read from stdin (for tests)." << endl;
  cout << "  record     Write a player's arrangements of random hands in the GUI's format." << endl;
  cout << "  gui        The play GUI's engine: JSON requests on stdin (for python/play_gui.py)." << endl;
  cout << endl;
  cout << "Run '" << program << " SUBCOMMAND -h' for a subcommand's arguments." << endl;
}

int main(int argc, const char* argv[]) {
  vector<string> args(argv, argv + argc);
  if(args.size() < 2 || args[1] == "-h" || args[1] == "--help" || args[1] == "help") {
    printHelp(args[0]);
    return args.size() < 2 ? 1 : 0;
  }
  string sub = args[1];
  // TCLAP sees "program subcommand" as the program name.
  vector<string> subArgs;
  subArgs.push_back(args[0] + " " + sub);
  subArgs.insert(subArgs.end(), args.begin() + 2, args.end());
  try {
    if(sub == "runtests")
      return Commands::runTests(subArgs);
    if(sub == "match")
      return Commands::match(subArgs);
    if(sub == "analyze")
      return Commands::analyze(subArgs);
    if(sub == "exploit")
      return Commands::exploit(subArgs);
    if(sub == "benchmark")
      return Commands::benchmark(subArgs);
    if(sub == "selfplay")
      return Commands::selfplay(subArgs);
    if(sub == "humanfeatures")
      return Commands::humanFeatures(subArgs);
    if(sub == "netq")
      return Commands::netQ(subArgs);
    if(sub == "evalkeys")
      return Commands::evalKeys(subArgs);
    if(sub == "record")
      return Commands::record(subArgs);
    if(sub == "gui")
      return Commands::gui(subArgs);
  }
  catch(const StringError& e) {
    cerr << "Error: " << e.what() << endl;
    return 1;
  }
  cerr << "Unknown subcommand: " << sub << endl;
  printHelp(args[0]);
  return 1;
}
