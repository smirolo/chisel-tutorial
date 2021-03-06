package TutorialExamples

import Chisel._
import Node._;
import Literal._;
import scala.collection.mutable.HashMap

class ReadCmd extends Bundle {
  val addr = UInt(width = 32);
}

class WriteCmd extends ReadCmd {
  val data = UInt(width = 32)
}

class Packet extends Bundle {
  val header = UInt(width = 8)
  val body   = Bits(width = 64)
}

class RouterIO(n: Int) extends Bundle {
  override def clone = new RouterIO(n).asInstanceOf[this.type]
  val reads   = new DeqIO(new ReadCmd())
  val replies = new EnqIO(UInt(width = 8))
  val writes  = new DeqIO(new WriteCmd())
  val in      = new DeqIO(new Packet())
  val outs    = Vec.fill(n){ new EnqIO(new Packet()) }
}

class Router extends Module {
  val depth = 32
  val n     = 4
  val io    = new RouterIO(n)
  val tbl   = Mem(UInt(width = sizeof(n)), depth)
  when(io.reads.valid && io.replies.ready) { 
    val cmd = io.reads.deq();  io.replies.enq(tbl(cmd.addr))  
  } .elsewhen(io.writes.valid) { 
    val cmd = io.writes.deq(); tbl(cmd.addr) := cmd.data
  } .elsewhen(io.in.valid) {
    val pkt = io.in.deq(); io.outs(tbl(pkt.header(0))).enq(pkt) 
  } 
}

class RouterTests(c: Router) extends Tester(c, Array(c.io)) {  
  defTests {
    var allGood = true
    val svars   = new HashMap[Node, Node]()
    val ovars   = new HashMap[Node, Node]()
    def rd(addr: UInt, data: UInt) = {
      svars.clear()
      svars(c.io.replies.ready) = Bool(true)
      svars(c.io.reads.valid)   = Bool(true)
      svars(c.io.reads.bits.addr)    = addr
      svars(c.io.replies.bits)  = data
      svars(c.io.replies.ready) = Bool(true)
      step(svars)
    }
    def wr(addr: UInt, data: UInt)  = {
      svars.clear()
      svars(c.io.writes.valid)     = Bool(true)
      svars(c.io.writes.bits.addr) = addr
      svars(c.io.writes.bits.data) = data
      step(svars)
    }
    def isAnyValidOuts(vars: HashMap[Node, Node]): Boolean = {
      for (out <- c.io.outs)
        if (vars(out.valid).litValue() == 1)
          return true
      false
    }
    def rt(header: UInt, body: Bits)  = {
      svars.clear()
      for (out <- c.io.outs)
        svars(out.ready)         = Bool(true)
      svars(c.io.in.valid)       = Bool(true)
      svars(c.io.in.bits.header) = header
      svars(c.io.in.bits.body)   = body
      do {
        step(svars, ovars)
      } while (!isAnyValidOuts(ovars))
      true
    }
    step(svars)
    allGood = rd(UInt(0), UInt(0)) && allGood
    allGood = wr(UInt(0), UInt(1)) && allGood
    allGood = rd(UInt(0), UInt(1)) && allGood
    allGood = rt(UInt(0), Bits(1)) && allGood
    allGood
  }
}
