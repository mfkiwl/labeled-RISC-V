package rocket

import Chisel._
import Node._
import Constants._
import Instructions._
import hwacha.Constants._

class ioDpathVecInterface extends Bundle
{
  val vcmdq_bits = Bits(SZ_VCMD, OUTPUT)
  val vximm1q_bits = Bits(SZ_VIMM, OUTPUT)
  val vximm2q_bits = Bits(SZ_VSTRIDE, OUTPUT)
  val eaddr = Bits(64, OUTPUT)
  val exception = Bool(OUTPUT)
}

class ioDpathVec extends Bundle
{
  val ctrl = new ioCtrlDpathVec().flip
  val iface = new ioDpathVecInterface()
  val valid = Bool(INPUT)
  val inst = Bits(32, INPUT)
  val waddr = UFix(5, INPUT)
  val raddr1 = UFix(5, INPUT)
  val vecbank = Bits(8, INPUT)
  val vecbankcnt = UFix(4, INPUT)
  val wdata = Bits(64, INPUT)
  val rs2 = Bits(64, INPUT)
  val vec_eaddr = Bits(64, INPUT)
  val vec_exception = Bool(INPUT)
  val wen = Bool(OUTPUT)
  val appvl = UFix(12, OUTPUT)
}

class rocketDpathVec extends Component
{
  val io = new ioDpathVec()

  val nxregs = Cat(UFix(0,1),io.inst(15,10).toUFix) // FIXME: to make the nregs width 7 bits
  val nfregs = io.inst(21,16).toUFix
  val nregs = nxregs + nfregs

  val uts_per_bank = MuxLookup(
    nregs, UFix(4,9), Array(
      UFix(0,7) -> UFix(256,9),
      UFix(1,7) -> UFix(256,9),
      UFix(2,7) -> UFix(256,9),
      UFix(3,7) -> UFix(128,9),
      UFix(4,7) -> UFix(85,9),
      UFix(5,7) -> UFix(64,9),
      UFix(6,7) -> UFix(51,9),
      UFix(7,7) -> UFix(42,9),
      UFix(8,7) -> UFix(36,9),
      UFix(9,7) -> UFix(32,9),
      UFix(10,7) -> UFix(28,9),
      UFix(11,7) -> UFix(25,9),
      UFix(12,7) -> UFix(23,9),
      UFix(13,7) -> UFix(21,9),
      UFix(14,7) -> UFix(19,9),
      UFix(15,7) -> UFix(18,9),
      UFix(16,7) -> UFix(17,9),
      UFix(17,7) -> UFix(16,9),
      UFix(18,7) -> UFix(15,9),
      UFix(19,7) -> UFix(14,9),
      UFix(20,7) -> UFix(13,9),
      UFix(21,7) -> UFix(12,9),
      UFix(22,7) -> UFix(12,9),
      UFix(23,7) -> UFix(11,9),
      UFix(24,7) -> UFix(11,9),
      UFix(25,7) -> UFix(10,9),
      UFix(26,7) -> UFix(10,9),
      UFix(27,7) -> UFix(9,9),
      UFix(28,7) -> UFix(9,9),
      UFix(29,7) -> UFix(9,9),
      UFix(30,7) -> UFix(8,9),
      UFix(31,7) -> UFix(8,9),
      UFix(32,7) -> UFix(8,9),
      UFix(33,7) -> UFix(8,9),
      UFix(34,7) -> UFix(7,9),
      UFix(35,7) -> UFix(7,9),
      UFix(36,7) -> UFix(7,9),
      UFix(37,7) -> UFix(7,9),
      UFix(38,7) -> UFix(6,9),
      UFix(39,7) -> UFix(6,9),
      UFix(40,7) -> UFix(6,9),
      UFix(41,7) -> UFix(6,9),
      UFix(42,7) -> UFix(6,9),
      UFix(43,7) -> UFix(6,9),
      UFix(44,7) -> UFix(5,9),
      UFix(45,7) -> UFix(5,9),
      UFix(46,7) -> UFix(5,9),
      UFix(47,7) -> UFix(5,9),
      UFix(48,7) -> UFix(5,9),
      UFix(49,7) -> UFix(5,9),
      UFix(50,7) -> UFix(5,9),
      UFix(51,7) -> UFix(5,9),
      UFix(52,7) -> UFix(5,9)
    ))

  val reg_hwvl = Reg(resetVal = UFix(32, 12))
  val reg_appvl0 = Reg(resetVal = Bool(true))
  val hwvl_vcfg = (uts_per_bank * io.vecbankcnt)(11,0)
  val hwvl = Mux(io.ctrl.fn === VEC_CFG, hwvl_vcfg, reg_hwvl)
  val appvl = Mux(io.wdata(11,0) < hwvl, io.wdata(11,0), hwvl).toUFix

  when (io.valid && io.ctrl.wen)
  {
    when (io.ctrl.fn === VEC_CFG) { reg_hwvl := hwvl_vcfg }
    reg_appvl0 := !(appvl.orR())
  }

  io.wen := io.valid && io.ctrl.wen
  io.appvl := appvl
  val vlenm1 = appvl - Bits(1,1)

  io.iface.vcmdq_bits :=
    Mux(io.ctrl.sel_vcmd === VCMD_I, Cat(Bits(0,2), Bits(0,4), io.inst(9,8), Bits(0,6), Bits(0,6)),
    Mux(io.ctrl.sel_vcmd === VCMD_F, Cat(Bits(0,2), Bits(1,3), io.inst(9,7), Bits(0,6), Bits(0,6)),
    Mux(io.ctrl.sel_vcmd === VCMD_TX, Cat(Bits(1,2), io.inst(13,8), Bits(0,1), io.waddr, Bits(0,1), io.raddr1),
    Mux(io.ctrl.sel_vcmd === VCMD_TF, Cat(Bits(1,2), io.inst(13,8), Bits(1,1), io.waddr, Bits(1,1), io.raddr1),
    Mux(io.ctrl.sel_vcmd === VCMD_MX, Cat(Bits(1,1), io.inst(13,12), io.inst(2), io.inst(10,7), Bits(0,1), io.waddr, Bits(0,1), io.waddr),
    Mux(io.ctrl.sel_vcmd === VCMD_MF, Cat(Bits(1,1), io.inst(13,12), io.inst(2), io.inst(10,7), Bits(1,1), io.waddr, Bits(1,1), io.waddr),
    Bits(0,20)))))))

  io.iface.vximm1q_bits :=
    Mux(io.ctrl.sel_vimm === VIMM_VLEN, Cat(Bits(0,29), io.vecbankcnt, io.vecbank, io.inst(21,10), vlenm1(10,0)),
    io.wdata) // VIMM_ALU

  io.iface.vximm2q_bits := io.rs2

  io.iface.eaddr := io.vec_eaddr
  io.iface.exception := io.vec_exception

  io.ctrl.valid := io.valid
  io.ctrl.inst := io.inst
  io.ctrl.appvl0 := reg_appvl0
}
