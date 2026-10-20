// SPDX-License-Identifier: LGPL-2.1-or-later
// DRNAS Native FreeCAD LAB: actual OCCT geometry-kernel smoke layer.
// This is a small proof-of-concept consumer of Open CASCADE Technology,
// not a compiled FreeCAD application or a full Android GUI.
#include <BRepPrimAPI_MakeBox.hxx>
#include <BRepPrimAPI_MakeCylinder.hxx>
#include <BRepPrimAPI_MakeSphere.hxx>
#include <BRepCheck_Analyzer.hxx>
#include <BRepGProp.hxx>
#include <BRepTools.hxx>
#include <GProp_GProps.hxx>
#include <Standard_Failure.hxx>
#include <TopoDS_Shape.hxx>

#include <cmath>
#include <string>

namespace {
bool valid_volume(const TopoDS_Shape& shape, double expected, double rel_tol = 1e-6)
{
  if (shape.IsNull() || !BRepCheck_Analyzer(shape).IsValid()) return false;
  GProp_GProps props;
  BRepGProp::VolumeProperties(shape, props);
  return std::abs(props.Mass() - expected) <= expected * rel_tol;
}
}  // namespace

extern "C" __attribute__((visibility("default")))
const char* drnas_occt_version()
{
  return "DRNAS Private FreeCAD LAB - Open CASCADE 7.9.3 - Android ARM64";
}

// 0 means all three genuine OCCT solids passed geometric checks.
extern "C" __attribute__((visibility("default")))
int drnas_occt_probe()
{
  try {
    const TopoDS_Shape box = BRepPrimAPI_MakeBox(20., 20., 20.).Shape();
    const TopoDS_Shape cylinder = BRepPrimAPI_MakeCylinder(4., 10.).Shape();
    const TopoDS_Shape sphere = BRepPrimAPI_MakeSphere(5.).Shape();
    constexpr double pi = 3.14159265358979323846;
    if (!valid_volume(box, 8000.)) return 1;
    if (!valid_volume(cylinder, pi * 160.)) return 2;
    if (!valid_volume(sphere, (4. / 3.) * pi * 125.)) return 3;
    return 0;
  }
  catch (const Standard_Failure&) {
    return -2;
  }
  catch (...) {
    return -3;
  }
}

// The directory must exist and be writable by the calling Android app.
// Files can be imported by desktop FreeCAD in later stages.
// This code is only cross-compiled in Stage 1; device execution is not yet verified.
extern "C" __attribute__((visibility("default")))
int drnas_occt_export_brep(const char* output_directory)
{
  if (!output_directory || !*output_directory) return -1;
  try {
    const std::string dir(output_directory);
    const TopoDS_Shape box = BRepPrimAPI_MakeBox(20., 20., 20.).Shape();
    const TopoDS_Shape cylinder = BRepPrimAPI_MakeCylinder(4., 10.).Shape();
    const TopoDS_Shape sphere = BRepPrimAPI_MakeSphere(5.).Shape();
    if (!BRepTools::Write(box, (dir + "/box.brep").c_str())) return 1;
    if (!BRepTools::Write(cylinder, (dir + "/cylinder.brep").c_str())) return 2;
    if (!BRepTools::Write(sphere, (dir + "/sphere.brep").c_str())) return 3;
    return 0;
  }
  catch (const Standard_Failure&) {
    return -2;
  }
  catch (...) {
    return -3;
  }
}
