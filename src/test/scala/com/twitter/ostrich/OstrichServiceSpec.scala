package com.twitter.ostrich

class OstrichServiceSpec extends AbstractSpec {
  "OstrichService" should {

    // TODO: Please implement your own tests.

    "set a key, get a key" in {
      ostrich.put("name", "bluebird")()
      ostrich.get("name")() mustEqual "bluebird"
      ostrich.get("what?")() must throwA[Exception]
    }
  }
}
