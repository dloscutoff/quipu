/*
 * Copyright 2013, by Vladimir Kostyukov and Contributors.
 * Modifications copyright 2021, by cgccuser and Contributors.
 *
 * This file is part of Quipu project (https://github.com/vkostyukov/quipu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributor(s):
 *  - David Loscutoff
 *  - cgccuser
 */

package quipu

import collection.mutable.ArrayBuffer

class ParserException(message: String) extends Exception(message)

object Parser {

  def apply(source: io.Source): Array[Array[Knot]] = {
    import Knot.*

    type Thread = ArrayBuffer[Knot]

    var threads: List[Int] = Nil // used to idents

    var strBuffer: Array[String] = Array()
    var intBuffer: Array[BigInt] = Array()

    var result: ArrayBuffer[Thread] = ArrayBuffer()

    source.getLines foreach { l =>
      val line: Seq[Char] = l

      var index = 0 // pointer to line character

      // perform the first line
      if (threads == Nil) {
        if (line.length > 0 && line(0) != '"') {
          while (index < line.length) {
            while (index < line.length && line(index).isWhitespace) index += 1

            if (index != line.length) {
              val t = new Thread()
              t += new NumberKnot(0)
              result += t
              threads = threads :+ index

              index += 2
            }
          }

          strBuffer = Array.fill(threads.length) { "" }
          intBuffer = Array.fill(threads.length) { -1 }
        }
      }

      threads.indices foreach { i =>
        index = threads(i)
        val thread = result(i)
        if (line.length > index + 1) {
          line(index) match {
            case '[' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              thread += ReferenceKnot
              intBuffer(i) = -1
              strBuffer(i) = ""
            case '^' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              thread += SelfKnot
              intBuffer(i) = -1
              strBuffer(i) = ""
            case c: Char if c.isDigit =>
              line(index + 1) match {
                case '&' =>
                  if (intBuffer(i) == -1) {
                    intBuffer(i) = 0
                  }
                  intBuffer(i) += BigInt(c.toString)
                case '@' =>
                  if (intBuffer(i) == -1) {
                    intBuffer(i) = 0
                  }
                  intBuffer(i) += BigInt(c.toString) * 10
                case '%' =>
                  if (intBuffer(i) == -1) {
                    intBuffer(i) = 0
                  }
                  intBuffer(i) += BigInt(c.toString) * 100
                case '#' =>
                  if (intBuffer(i) == -1) {
                    intBuffer(i) = 0
                  }
                  if (intBuffer(i) != 0) {
                    intBuffer(i) = intBuffer(i) * 10
                  }
                  intBuffer(i) += BigInt(c.toString) * 1000
              }
            case '\'' => strBuffer(i) += line(index + 1)
            case '\\' =>
              line(index + 1) match {
                case 'n' => strBuffer(i) += '\n'
                case 't' => strBuffer(i) += '\t'
                case '/' =>
                  dumpBuffers(intBuffer(i), strBuffer(i), thread)
                  thread += InKnot
                  intBuffer(i) = -1
                  strBuffer(i) = ""
              }
            case '+' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              thread += new OperationKnot((x, y) => x + y)
              intBuffer(i) = -1
              strBuffer(i) = ""
            case '-' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              thread += new OperationKnot((x, y) => x - y)
              intBuffer(i) = -1
              strBuffer(i) = ""
            case '*' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              thread += new OperationKnot((x, y) => x * y)
              intBuffer(i) = -1
              strBuffer(i) = ""
            case '/' =>
              line(index + 1) match {
                case '\\' =>
                  dumpBuffers(intBuffer(i), strBuffer(i), thread)
                  thread += OutKnot
                  intBuffer(i) = -1
                  strBuffer(i) = ""
                case '/' =>
                  dumpBuffers(intBuffer(i), strBuffer(i), thread)
                  thread += new OperationKnot((x, y) => x / y)
                  intBuffer(i) = -1
                  strBuffer(i) = ""
              }
            case '%' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              thread += new OperationKnot((x, y) => x % y)
              intBuffer(i) = -1
              strBuffer(i) = ""
            case '=' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              thread += new ConditionalJumpKnot((x => x == 0))
              intBuffer(i) = -1
              strBuffer(i) = ""
            case '?' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              thread += JumpKnot
              intBuffer(i) = -1
              strBuffer(i) = ""
            case '<' =>
              line(index + 1) match {
                case '<' =>
                  dumpBuffers(intBuffer(i), strBuffer(i), thread)
                  thread += new ConditionalJumpKnot((x => x < 0))
                  intBuffer(i) = -1
                  strBuffer(i) = ""
                case '=' =>
                  dumpBuffers(intBuffer(i), strBuffer(i), thread)
                  thread += new ConditionalJumpKnot((x => x <= 0))
                  intBuffer(i) = -1
                  strBuffer(i) = ""
              }
            case '>' =>
              line(index + 1) match {
                case '>' =>
                  dumpBuffers(intBuffer(i), strBuffer(i), thread)
                  thread += new ConditionalJumpKnot((x => x > 0))
                  intBuffer(i) = -1
                  strBuffer(i) = ""
                case '=' =>
                  dumpBuffers(intBuffer(i), strBuffer(i), thread)
                  thread += new ConditionalJumpKnot((x => x >= 0))
                  intBuffer(i) = -1
                  strBuffer(i) = ""
              }
            case ';' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              intBuffer(i) = -1
              strBuffer(i) = ""
            case ':' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              thread += HaltKnot
              intBuffer(i) = -1
              strBuffer(i) = ""
            case '#' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              thread += CopyKnot
              intBuffer(i) = -1
              strBuffer(i) = ""
            case ' ' =>
              dumpBuffers(intBuffer(i), strBuffer(i), thread)
              strBuffer(i) = ""
              intBuffer(i) = -1
          }
        }
      }
    }

    source.close()

    // dump all buffers
    threads.indices foreach { i =>
      val thread = result(i)
      dumpBuffers(intBuffer(i), strBuffer(i), thread)
    }

    val array: Array[Array[Knot]] = new Array(result.length)
    result.indices foreach { i =>
      array(i) = result(i).toArray
    }

    return array
  }

  private def dumpBuffers(i: BigInt, s: String, thread: ArrayBuffer[Knot]) = {
    if (i != -1) {
      thread += Knot.NumberKnot(i)
    }
    if (s != "") {
      thread += Knot.StringKnot(s)
    }
  }
}
