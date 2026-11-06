// The web page's engine: GuiEngine (cpp/command/guiengine.h) behind two C calls, one JSON request line in and one
// answer line out, as the Android app's JNI wrapper has it. Run in a worker, one request at a time.
#include "../../cpp/command/guiengine.h"

#include <cstdlib>
#include <cstring>
#include <string>

static GuiEngine& engine() {
  static GuiEngine e;
  return e;
}

extern "C" {

// The answer to `request` (UTF-8), in memory the caller frees with engine_free.
char* engine_handle(const char* request) {
  std::string out = engine().handleLine(request);
  char* p = static_cast<char*>(std::malloc(out.size() + 1));
  std::memcpy(p, out.c_str(), out.size() + 1);
  return p;
}

void engine_free(char* p) { std::free(p); }

}
