#ifndef COMMAND_GUIENGINE_H_
#define COMMAND_GUIENGINE_H_

#include "../players/player.h"

#include <nlohmann/json.hpp>

#include <map>
#include <memory>

class NetModel;

// The engine of the play GUIs (python/play_gui.py through `thirteen gui`, and the Android app through JNI): one JSON
// request, one JSON answer. Cards are ids (rank * 4 + suit); an arrangement is [front, middle, back], each a list of card ids.
// Every request may have "rules", a spec as in Rules::parse (default family), and "c", the dealer cost (default 0), which
// replaces the spec's.
//
//   {"cmd": "deal", "seed": S}                       -> {"hands": [4 hands]}
//   {"cmd": "arrange", "player": SPEC, "hand": H, "c": C, "seed": S}
//                                                    -> {"arrangement": A, "info": INFO}
//   {"cmd": "analyze", "hand": H, "c": C, "model": PATH, "pop": P, "extra": [A, ...]}
//       The undominated arrangements plus `extra`; with a model, each with its Q and event probabilities, best Q
//       first.                                       -> {"cands": [{"arrangement", "info", "q", "probs", "extra"}],
//                                                        "opp_royalty", "opp_bonus", "num_valid", "dragon", "rules",
//                                                        "legacy_model"}
//       opp_royalty: the opponents' expected royalties per type and dragons (Scoring::NUM_OPP_ROYALTIES); opp_bonus:
//       what they cost when royalties are paid whatever the rows do (under royaltyOnWin, only the dragons).
//   {"cmd": "mc", "hand": H, "c": C, "opp": SPEC, "samples": N, "cands": [A, ...], "seed": S}
//       Monte Carlo means against three SPEC opponents, in the order given.  -> {"stats": [{"mean", "std_err"}]}
//   {"cmd": "score", "arrangements": [4 A], "c": C}  -> scores as in DealScore, plus each arrangement's info and
//                                                       "pairs": pairs[i][j] is what j pays i, home runs included
//   {"cmd": "rows", "rows": [front, middle, back]}   Rows being filled, each with any number of cards
//                                                    -> {"keys", "categories"} (0 for a row that is not full), plus
//                                                       "valid", "bonus" and "royalties" once all rows are full
//   {"cmd": "rules"}                                 The rules checked -> {"rules": full spec, "values": {name: value}}
//   {"cmd": "model", "model": PATH}                  -> {"legacy"}: the network predates the rules input
// INFO is {"keys": [3], "categories": [3], "valid", "bonus", "royalties": [3], "dragon"}; royalties are each row's
// under the rules. Errors answer {"error": MESSAGE}.
class GuiEngine {
 public:
  // Throws on a bad request.
  nlohmann::json handle(const nlohmann::json& req);
  // One request line to one answer line; errors answer {"error": MESSAGE}.
  std::string handleLine(const std::string& line);

 private:
  std::map<std::string, std::shared_ptr<const Player>> players;
  std::map<std::string, std::shared_ptr<const NetModel>> models;

  std::shared_ptr<const Player> player(const std::string& spec);
  std::shared_ptr<const NetModel> model(const std::string& path);
  nlohmann::json analyze(const nlohmann::json& req);
};

#endif // COMMAND_GUIENGINE_H_
