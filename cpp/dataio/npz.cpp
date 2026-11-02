#include "../dataio/npz.h"

#include <cstdio>
#include <fstream>

using namespace std;

uint32_t Npz::crc32(const void* data, size_t len) {
  static uint32_t table[256];
  static bool init = [] {
    for(uint32_t i = 0; i < 256; i++) {
      uint32_t c = i;
      for(int k = 0; k < 8; k++)
        c = (c & 1) ? 0xEDB88320u ^ (c >> 1) : c >> 1;
      table[i] = c;
    }
    return true;
  }();
  (void)init;
  uint32_t c = 0xFFFFFFFFu;
  const uint8_t* p = (const uint8_t*)data;
  for(size_t i = 0; i < len; i++)
    c = table[(c ^ p[i]) & 0xFF] ^ (c >> 8);
  return c ^ 0xFFFFFFFFu;
}

void NpzWriter::addRaw(const string& name, const char* descr, const vector<int64_t>& shape, const void* data,
                       size_t numBytes) {
  string shapeStr = "(";
  for(size_t i = 0; i < shape.size(); i++) {
    shapeStr += Global::int64ToString(shape[i]);
    if(shape.size() == 1 || i + 1 < shape.size())
      shapeStr += ",";
    if(i + 1 < shape.size())
      shapeStr += " ";
  }
  shapeStr += ")";
  string header = string("{'descr': '") + descr + "', 'fortran_order': False, 'shape': " + shapeStr + ", }";
  // Magic (6) + version (2) + header length (2) + header, padded with spaces and ending in '\n', to a multiple of 64.
  size_t total = 10 + header.size() + 1;
  size_t padded = (total + 63) / 64 * 64;
  header += string(padded - total, ' ');
  header += '\n';

  string bytes;
  bytes += "\x93NUMPY";
  bytes += (char)1;
  bytes += (char)0;
  uint16_t headerLen = (uint16_t)header.size();
  bytes += (char)(headerLen & 0xFF);
  bytes += (char)(headerLen >> 8);
  bytes += header;
  bytes.append((const char*)data, numBytes);
  entries.push_back({name + ".npy", bytes});
}

#define NPZ_ADD(T, DESCR) \
  void NpzWriter::add(const string& name, const vector<int64_t>& shape, const vector<T>& data) { \
    addRaw(name, DESCR, shape, data.data(), data.size() * sizeof(T)); \
  }
NPZ_ADD(uint8_t, "|u1")
NPZ_ADD(uint16_t, "<u2")
NPZ_ADD(int32_t, "<i4")
NPZ_ADD(int64_t, "<i8")
NPZ_ADD(uint32_t, "<u4")
NPZ_ADD(float, "<f4")
#undef NPZ_ADD

static void put16(string& s, uint16_t x) {
  s += (char)(x & 0xFF);
  s += (char)(x >> 8);
}
static void put32(string& s, uint32_t x) {
  for(int i = 0; i < 4; i++)
    s += (char)((x >> (8 * i)) & 0xFF);
}

void NpzWriter::write(const string& path) const {
  string tmpPath = path + ".tmp";
  ofstream out(tmpPath, ios::binary);
  if(!out)
    throw IOError("Could not open " + tmpPath);
  string central;
  uint64_t offset = 0;
  for(const Entry& e: entries) {
    if(e.bytes.size() >= 0xFFFFFFFFull || offset >= 0xFFFFFFFFull)
      throw IOError("npz entry too large for a zip without zip64: " + e.name);
    uint32_t crc = Npz::crc32(e.bytes.data(), e.bytes.size());
    string local;
    put32(local, 0x04034b50);
    put16(local, 20);  // version needed
    put16(local, 0);   // flags
    put16(local, 0);   // method: stored
    put16(local, 0);   // time
    put16(local, 0x21); // date (1980-01-01)
    put32(local, crc);
    put32(local, (uint32_t)e.bytes.size());
    put32(local, (uint32_t)e.bytes.size());
    put16(local, (uint16_t)e.name.size());
    put16(local, 0);
    local += e.name;
    out.write(local.data(), local.size());
    out.write(e.bytes.data(), e.bytes.size());

    put32(central, 0x02014b50);
    put16(central, 20);
    put16(central, 20);
    put16(central, 0);
    put16(central, 0);
    put16(central, 0);
    put16(central, 0x21);
    put32(central, crc);
    put32(central, (uint32_t)e.bytes.size());
    put32(central, (uint32_t)e.bytes.size());
    put16(central, (uint16_t)e.name.size());
    put16(central, 0);
    put16(central, 0);
    put16(central, 0);
    put16(central, 0);
    put32(central, 0);
    put32(central, (uint32_t)offset);
    central += e.name;
    offset += local.size() + e.bytes.size();
  }
  string end;
  put32(end, 0x06054b50);
  put16(end, 0);
  put16(end, 0);
  put16(end, (uint16_t)entries.size());
  put16(end, (uint16_t)entries.size());
  put32(end, (uint32_t)central.size());
  put32(end, (uint32_t)offset);
  put16(end, 0);
  out.write(central.data(), central.size());
  out.write(end.data(), end.size());
  out.close();
  if(!out)
    throw IOError("Error writing " + tmpPath);
  if(std::rename(tmpPath.c_str(), path.c_str()) != 0)
    throw IOError("Could not rename " + tmpPath + " to " + path);
}
