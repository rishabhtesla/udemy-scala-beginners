package lectures.part5advanced

object UnionAndIntersectionTypes extends App {

  /*
    Union type A | B = a value can be either A or B.
    Intersection type A & B = a value must satisfy both A and B.
   */

  def stringify(value: Int | String): String = value match {
    case number: Int => s"number: $number"
    case text: String => s"text: $text"
  }

  println(stringify(42))
  println(stringify("Scala"))

  trait Camera {
    def takePhoto(): String
  }

  trait Phone {
    def makeCall(): String
  }

  class SmartPhone extends Camera with Phone {
    def takePhoto(): String = "photo taken"
    def makeCall(): String = "calling..."
  }

  def useDevice(device: Camera & Phone): Unit = {
    println(device.takePhoto())
    println(device.makeCall())
  }

  useDevice(new SmartPhone)
}

