package com.raquo.waypoint

import urldsl.language.Fragment
import urldsl.errors.DummyError


  // /** Create a route for a static page that does not encode any data in the URL */
  // def static[Page](
  //   staticPage: Page,
  //   pattern: PathSegment[Unit, DummyError]
  // ): Route[Page, Unit] = {
  //   new Route[Page, Unit](
  //     matchEncodePF = { case p if p == staticPage => () },
  //     decodePF = { case _ => staticPage },
  //     createRelativeUrl = args => "/" + pattern.createPath(args),
  //     matchRelativeUrl = relativeUrl => pattern.matchRawUrl(relativeUrl).toOption
  //   )
  // }
// /** Encoding and decoding are partial functions. Their partial-ness should be symmetrical,
//   * otherwise you'll end up with a route that can parse a URL into a Page but can't encode
//   * the page into the same URL (or vice versa)
//   *
//   * @param matchEncodePF - Match Any to Page, and if successful, encode it into Args
//   * @param decodePF      - Decode Args into Page, if args are valid
//   * @tparam Page         - Types of pages that this Route is capable of matching.
//   *                        Note: the Route might match only a subset of pages of this type.
//   * @tparam Args         - Type of data saved in the URL for pages matched by this route
//   *                        Note: the Route might match only a subset of args of this type.
//   */
// class Route[Page, Args] private(
//   matchEncodePF: PartialFunction[Any, Args],
//   decodePF: PartialFunction[Args, Page],
//   createRelativeUrl: Args => String,
//   matchRelativeUrl: String => Option[Args]
// ) {


  // val pageRoute = Route(
  //   encode = (pg: Page) => pg.path.toList,
  //   decode = (arg: String) => Page(arg.jsSplit("/")),
  //   pattern = fragment[String]
  // )

  // val testPageRoute = new Route[Page, String](
  //   matchEncodePF = {
  //     case p: Page => p.path.toString
  //   } 
  // )
  //
// object RouteExt {
//   def fragmentOnly[Page](
//     encode: Page => String,
//     decode: String => Page,
//     pattern: Fragment[String, DummyError]
//   ) = new Route[Page, String](
//     matchEncodePF = {
//       case p: Page => encode(p)
//     },
//     decodePF = {
//       case str => decode(str)
//     },
//     createRelativeUrl = args => 
//       "/" + args
//   )
// }

