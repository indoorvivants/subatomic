package subatomic
package search

/** Anton:
  *
  * This is just the initial implementation, copied from https://tartarus.org/martin/PorterStemmer/scala.txt
  *
  * I intend to rewrite it in a more condensed way, reducing the number of passes.
  *
  * I've insourced it for now, given that the licensing is permissive:
  *
  * "In every case where the software is not written by me (Martin Porter), this licensing arrangement has been endorsed
  * by the contributor, and it is therefore unnecessary to ask the contributor again to confirm it."
  *
  * See FAW at https://tartarus.org/martin/PorterStemmer/ *
  */

object PorterStemmer {

  def stem(tok: String) = {
    var b = new String(tok)

    // Just recode the existing stuff, then go through and refactor with some intelligence.
    def cons(i: Int): Boolean = {
      val ch = b(i)

      // magic!
      val vowels = "aeiou"

      // multi return. yuck
      if (vowels.contains(ch))
        return false

      if (ch == 'y') {
        if (i == 0) {
          return true
        } else {
          // loop it!
          return !cons(i - 1)
        }
      }

      return true

    }

    /* m() measures the number of consonant sequences between 0 and j. if c is
      a consonant sequence and v a vowel sequence, and <..> indicates arbitrary
      presence,

         <c><v>       gives 0
         <c>vc<v>     gives 1
         <c>vcvc<v>   gives 2
         <c>vcvcvc<v> gives 3
         ....
     *
     * I think this can be recoded far more neatly.
     */

    def calcM(s: String): Int = {
      val l            = s.length
      var count        = 0
      var currentConst = false

      for (c <- 0 to l - 1) {
        if (cons(c)) {
          if (!currentConst && c != 0) {
            count += 1
          }
          currentConst = true
        } else {
          currentConst = false
        }
      }

      return count
    }

    // removing the suffix 's', does a vowel exist?'
    def vowelInStem(s: String): Boolean = {

      for (i <- 0 to b.length - 1 - s.length) {
        if (!cons(i)) {
          return true
        }
      }

      return false;

    }

    /* doublec(j) is true <=> j,(j-1) contain a double consonant. */
    def doublec(): Boolean = {
      val l = b.length - 1

      if (l < 1)
        return false

      if (b(l) != b(l - 1))
        return false

      return cons(l)

    }

    /* cvc(i) is true <=> i-2,i-1,i has the form consonant - vowel - consonant
      and also if the second c is not w,x or y. this is used when trying to
      restore an e at the end of a short word. e.g.

         cav(e), lov(e), hop(e), crim(e), but
         snow, box, tray.

     */

    def cvc(s: String): Boolean = {
      val i = b.length - 1 - s.length
      if (i < 2 || !cons(i) || cons(i - 1) || !cons(i - 2))
        return false;

      val ch = b(i)

      val vals = "wxy"

      if (vals.contains(ch))
        return false

      return true;
    }

    // returns true if it did the change.
    def replacer(orig: String, replace: String, checker: Int => Boolean): Boolean = {

      val l          = b.length
      val origLength = orig.length
      var res        = false

      if (b.endsWith(orig)) {
        val n = b.substring(0, l - origLength)

        val m = calcM(n)
        if (checker(m)) {
          b = n + replace
        }

        res = true

      }

      return res
    }

    // process the list of tuples to find which prefix matches the case.
    // checker is the conditional checker for m.
    def processSubList(l: List[(String, String)], checker: Int => Boolean): Boolean = {
      val iter = l.iterator
      var done = false

      while (!done && iter.hasNext) {
        val v = iter.next()
        done = replacer(v._1, v._2, checker)

      }

      return done
    }

    def step1(): Unit = {

      // val l = b.length

      var m = calcM(b)

      // step 1a
      var vals = List(("sses", "ss"), ("ies", "i"), ("ss", "ss"), ("s", ""))
      processSubList(vals, _ >= 0)

      // step 1b
      if (!(replacer("eed", "ee", _ > 0))) {

        if ((vowelInStem("ed") && replacer("ed", "", _ >= 0)) || (vowelInStem("ing") && replacer("ing", "", _ >= 0))) {

          vals = List(("at", "ate"), ("bl", "ble"), ("iz", "ize"))

          if (!processSubList(vals, _ >= 0)) {
            // if this isn't done, then it gets more confusing.

            m = calcM(b)
            val last = b(b.length - 1)
            if (doublec() && !"lsz".contains(last)) {
              b = b.substring(0, b.length - 1)
            } else if (m == 1 && cvc("")) {
              b = b + "e"
            }
          }
        }
      }

      // step 1c

      val _ = (vowelInStem("y") && replacer("y", "i", _ >= 0))
    }

    def step2() = {

      val vals = List(
        ("ational", "ate"),
        ("tional", "tion"),
        ("enci", "ence"),
        ("anci", "ance"),
        ("izer", "ize"),
        ("bli", "ble"),
        ("alli", "al"),
        ("entli", "ent"),
        ("eli", "e"),
        ("ousli", "ous"),
        ("ization", "ize"),
        ("ation", "ate"),
        ("ator", "ate"),
        ("alism", "al"),
        ("iveness", "ive"),
        ("fulness", "ful"),
        ("ousness", "ous"),
        ("aliti", "al"),
        ("iviti", "ive"),
        ("biliti", "ble"),
        ("logi", "log")
      )

      processSubList(vals, _ > 0)

    }

    def step3() = {

      val vals =
        List(
          ("icate", "ic"),
          ("ative", ""),
          ("alize", "al"),
          ("iciti", "ic"),
          ("ical", "ic"),
          ("ful", ""),
          ("ness", "")
        )

      processSubList(vals, _ > 0)

    }

    def step4() = {

      // first part.
      val vals = List(
        ("al", ""),
        ("ance", ""),
        ("ence", ""),
        ("er", ""),
        ("ic", ""),
        ("able", ""),
        ("ible", ""),
        ("ant", ""),
        ("ement", ""),
        ("ment", ""),
        ("ent", "")
      )

      var res = processSubList(vals, _ > 1)

      // special part.
      if (!res) {
        if (b.length > 4) {
          if (b(b.length - 4) == 's' || b(b.length - 4) == 't') {
            res = replacer("ion", "", _ > 1)

          }
        }

      }

      // third part.
      if (!res) {
        val vals = List(("ou", ""), ("ism", ""), ("ate", ""), ("iti", ""), ("ous", ""), ("ive", ""), ("ize", ""))
        res = processSubList(vals, _ > 1)

      }

    }

    def step5a() = {

      var res = false

      res = replacer("e", "", _ > 1)

      if (!cvc("e")) {
        res = replacer("e", "", _ == 1)
      }

    }

    def step5b() = {

      val m = calcM(b)
      if (m > 1 && doublec() && b.endsWith("l")) {
        b = b.substring(0, b.length - 1)
      }

    }

    step1()
    step2()
    step3()
    step4()
    step5a()
    step5b()

    b

  }

}
