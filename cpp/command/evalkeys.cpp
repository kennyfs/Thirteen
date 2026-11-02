#include "../command/commands.h"

#include "../game/handeval.h"

#include <iostream>

using namespace std;

// Reads lines of 3 or 5 cards from stdin and prints each one's key, for testing other implementations (gui/).
int Commands::evalKeys(vector<string> args) {
  (void)args;
  string line;
  while(getline(cin, line)) {
    if(Global::trim(line).empty())
      continue;
    CardSet s = Cards::parseCardSet(line);
    int n = Cards::count(s);
    if(n == 5)
      cout << HandEval::eval5(s) << "\n";
    else if(n == 3)
      cout << HandEval::eval3(s) << "\n";
    else
      throw StringError("Expected 3 or 5 cards: " + line);
  }
  return 0;
}
