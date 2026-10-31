// A small Z-machine interpreter for version 3 story files (Zork I-III and friends).
// It has no UI of its own: output, the status line, saving and quitting go through `io`.
//
//   io.print(text)               game text
//   io.status(location, right)   status line, e.g. ("West of House", "Score: 0  Moves: 1")
//   io.save(snapshot) -> bool    store a snapshot for the game's SAVE command
//   io.restore() -> snapshot     snapshot for RESTORE, or null
//   io.restart()                 game restarted (clear the screen)
//   io.quit()                    game ended
//
// run() executes until the game wants a line of input (machine.waiting is set) or quits;
// then call input(line).
(function (root) {
  'use strict';

  const A0 = 'abcdefghijklmnopqrstuvwxyz';
  const A1 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  // Index 0 is the ZSCII escape and index 1 is newline; neither is a printable entry.
  const A2 = '\0\n0123456789.,!?_#\'"/\\-:()';

  const s16 = (v) => (v & 0x8000 ? v - 0x10000 : v);

  class ZMachine {
    constructor(story, io) {
      this.story = story instanceof Uint8Array ? story : new Uint8Array(story);
      if (this.story[0] !== 3) throw new Error('Only version 3 story files are supported');
      this.io = io;
      this.reset();
    }

    reset() {
      this.mem = new Uint8Array(this.story);
      this.dict = this.rw(0x08);
      this.objTable = this.rw(0x0a);
      this.globals = this.rw(0x0c);
      this.staticBase = this.rw(0x0e);
      this.abbrev = this.rw(0x18);
      // Status line available, no split screen.
      this.mem[1] &= ~0x30;
      this.pc = this.rw(0x06);
      this.stack = [];
      this.frames = [];
      this.frame = { locals: [], retPC: 0, storeVar: null, stackBase: 0 };
      this.memStreams = [];
      this.waiting = null;
      this.halted = false;
      this.rng = null;
    }

    // ---- memory ----
    rb(a) { return this.mem[a]; }
    rw(a) { return (this.mem[a] << 8) | this.mem[a + 1]; }
    wb(a, v) { this.mem[a] = v & 0xff; }
    ww(a, v) { this.mem[a] = (v >> 8) & 0xff; this.mem[a + 1] = v & 0xff; }

    get release() { return this.rw(0x02); }
    get serial() { return String.fromCharCode(...this.story.subarray(0x12, 0x18)); }

    // ---- variables ----
    readVar(v) {
      if (v === 0) {
        if (!this.stack.length) throw new Error('Stack underflow');
        return this.stack.pop();
      }
      if (v < 16) return this.frame.locals[v - 1];
      return this.rw(this.globals + 2 * (v - 16));
    }
    writeVar(v, val) {
      val &= 0xffff;
      if (v === 0) this.stack.push(val);
      else if (v < 16) this.frame.locals[v - 1] = val;
      else this.ww(this.globals + 2 * (v - 16), val);
    }
    // Opcodes that name a variable (inc, store, pull...) touch the top of the stack in place.
    peekVarRef(v) {
      if (v === 0) return this.stack[this.stack.length - 1];
      return this.readVar(v);
    }
    pokeVarRef(v, val) {
      if (v === 0) this.stack[this.stack.length - 1] = val & 0xffff;
      else this.writeVar(v, val);
    }

    // ---- control flow ----
    operand(type) {
      if (type === 0) { const v = this.rw(this.pc); this.pc += 2; return v; }
      if (type === 1) return this.mem[this.pc++];
      return this.readVar(this.mem[this.pc++]);
    }
    store(val) { this.writeVar(this.mem[this.pc++], val); }
    branch(cond) {
      const b = this.mem[this.pc++];
      let off;
      if (b & 0x40) off = b & 0x3f;
      else {
        off = ((b & 0x3f) << 8) | this.mem[this.pc++];
        if (off & 0x2000) off -= 0x4000;
      }
      if (!!cond === !!(b & 0x80)) {
        if (off === 0) this.ret(0);
        else if (off === 1) this.ret(1);
        else this.pc += off - 2;
      }
    }
    call(paddr, args, storeVar) {
      if (paddr === 0) { this.writeVar(storeVar, 0); return; }
      let a = paddr * 2;
      const n = this.mem[a++];
      const locals = [];
      for (let i = 0; i < n; i++, a += 2) locals.push(i < args.length ? args[i] : this.rw(a));
      this.frames.push(this.frame);
      this.frame = { locals, retPC: this.pc, storeVar, stackBase: this.stack.length };
      this.pc = a;
    }
    ret(val) {
      const f = this.frame;
      if (!this.frames.length) throw new Error('Return from main routine');
      this.stack.length = f.stackBase;
      this.frame = this.frames.pop();
      this.pc = f.retPC;
      if (f.storeVar !== null) this.writeVar(f.storeVar, val);
    }

    // ---- objects ----
    obj(o) { return this.objTable + 62 + (o - 1) * 9; }
    parent(o) { return o ? this.mem[this.obj(o) + 4] : 0; }
    sibling(o) { return o ? this.mem[this.obj(o) + 5] : 0; }
    child(o) { return o ? this.mem[this.obj(o) + 6] : 0; }
    testAttr(o, a) { return o ? (this.mem[this.obj(o) + (a >> 3)] & (0x80 >> (a & 7))) !== 0 : false; }
    setAttr(o, a, on) {
      if (!o) return;
      const addr = this.obj(o) + (a >> 3), bit = 0x80 >> (a & 7);
      this.mem[addr] = on ? this.mem[addr] | bit : this.mem[addr] & ~bit;
    }
    removeObj(o) {
      const p = this.parent(o);
      if (!o || !p) return;
      const oa = this.obj(o);
      if (this.child(p) === o) this.mem[this.obj(p) + 6] = this.sibling(o);
      else {
        let c = this.child(p);
        while (c && this.sibling(c) !== o) c = this.sibling(c);
        if (c) this.mem[this.obj(c) + 5] = this.sibling(o);
      }
      this.mem[oa + 4] = 0;
      this.mem[oa + 5] = 0;
    }
    insertObj(o, dest) {
      if (!o || !dest) return;
      this.removeObj(o);
      const oa = this.obj(o), da = this.obj(dest);
      this.mem[oa + 5] = this.mem[da + 6];
      this.mem[oa + 4] = dest;
      this.mem[da + 6] = o;
    }
    objName(o) {
      if (!o) return '';
      const p = this.rw(this.obj(o) + 7);
      return this.mem[p] ? this.decode(p + 1).text : '';
    }
    firstProp(o) {
      const p = this.rw(this.obj(o) + 7);
      return p + 1 + 2 * this.mem[p];
    }
    // Address of the size byte of property `prop` on `o`, or 0.
    findProp(o, prop) {
      if (!o) return 0;
      for (let p = this.firstProp(o); this.mem[p]; p += 2 + (this.mem[p] >> 5)) {
        const n = this.mem[p] & 31;
        if (n === prop) return p;
        if (n < prop) return 0;
      }
      return 0;
    }
    getProp(o, prop) {
      const p = this.findProp(o, prop);
      if (!p) return this.rw(this.objTable + 2 * (prop - 1));
      return (this.mem[p] >> 5) === 0 ? this.mem[p + 1] : this.rw(p + 1);
    }
    putProp(o, prop, val) {
      const p = this.findProp(o, prop);
      if (!p) throw new Error(`Object ${o} has no property ${prop}`);
      if ((this.mem[p] >> 5) === 0) this.wb(p + 1, val);
      else this.ww(p + 1, val);
    }
    nextProp(o, prop) {
      if (!o) return 0;
      let p;
      if (prop === 0) p = this.firstProp(o);
      else {
        p = this.findProp(o, prop);
        if (!p) throw new Error(`Object ${o} has no property ${prop}`);
        p += 2 + (this.mem[p] >> 5);
      }
      return this.mem[p] & 31;
    }

    // ---- text ----
    decode(addr) {
      const zs = [];
      for (;;) {
        const w = this.rw(addr);
        addr += 2;
        zs.push((w >> 10) & 31, (w >> 5) & 31, w & 31);
        if (w & 0x8000) break;
      }
      let text = '', alpha = 0;
      for (let i = 0; i < zs.length; i++) {
        const c = zs[i];
        if (c === 0) { text += ' '; alpha = 0; }
        else if (c <= 3) {
          if (++i >= zs.length) break;
          text += this.decode(this.rw(this.abbrev + 2 * (32 * (c - 1) + zs[i])) * 2).text;
          alpha = 0;
        }
        else if (c === 4) alpha = 1;
        else if (c === 5) alpha = 2;
        else if (alpha === 2 && c === 6) {
          if (i + 2 >= zs.length) break;
          text += this.zscii((zs[i + 1] << 5) | zs[i + 2]);
          i += 2;
          alpha = 0;
        }
        else { text += (alpha === 0 ? A0 : alpha === 1 ? A1 : A2)[c - 6]; alpha = 0; }
      }
      return { text, end: addr };
    }
    zscii(n) {
      if (n === 13) return '\n';
      if (n >= 32 && n <= 126) return String.fromCharCode(n);
      return '';
    }
    encode(word) {
      const zs = [];
      for (const ch of word) {
        if (zs.length >= 6) break;
        const i = A0.indexOf(ch);
        if (i >= 0) { zs.push(i + 6); continue; }
        const j = A2.indexOf(ch, 2);
        if (j >= 2) { zs.push(5, j + 6); continue; }
        const code = ch.charCodeAt(0) & 0x3ff;
        zs.push(5, 6, code >> 5, code & 31);
      }
      while (zs.length < 6) zs.push(5);
      return [(zs[0] << 10) | (zs[1] << 5) | zs[2], 0x8000 | (zs[3] << 10) | (zs[4] << 5) | zs[5]];
    }
    lookup(word) {
      const [w1, w2] = this.encode(word);
      let p = this.dict + 1 + this.mem[this.dict];
      const len = this.mem[p];
      const count = Math.abs(s16(this.rw(p + 1)));
      p += 3;
      for (let i = 0; i < count; i++, p += len) {
        if (this.rw(p) === w1 && this.rw(p + 2) === w2) return p;
      }
      return 0;
    }
    tokenize(textAddr, parseAddr) {
      const seps = Array.from(this.mem.subarray(this.dict + 1, this.dict + 1 + this.mem[this.dict]));
      const chars = [];
      for (let i = 1; this.mem[textAddr + i]; i++) chars.push(this.mem[textAddr + i]);
      const words = [];
      let start = -1;
      for (let i = 0; i <= chars.length; i++) {
        const c = i < chars.length ? chars[i] : 32;
        if (c === 32 || seps.includes(c)) {
          if (start >= 0) words.push([start, i - start]);
          start = -1;
          if (c !== 32) words.push([i, 1]);
        } else if (start < 0) start = i;
      }
      const n = Math.min(words.length, this.mem[parseAddr]);
      this.wb(parseAddr + 1, n);
      for (let k = 0; k < n; k++) {
        const [s, l] = words[k];
        const word = String.fromCharCode(...chars.slice(s, s + l));
        const e = parseAddr + 2 + 4 * k;
        this.ww(e, this.lookup(word));
        this.wb(e + 2, l);
        this.wb(e + 3, s + 1);
      }
    }

    // ---- output ----
    print(text) {
      if (!text) return;
      if (this.memStreams.length) {
        const s = this.memStreams[this.memStreams.length - 1];
        for (const ch of text) this.wb(s.table + 2 + s.len++, ch === '\n' ? 13 : ch.charCodeAt(0));
        return;
      }
      this.io.print(text);
    }
    updateStatus() {
      const loc = this.objName(this.readVar(16));
      const a = this.readVar(17), b = this.readVar(18);
      let right;
      if (this.mem[1] & 0x02) {
        const h = a % 12 || 12;
        right = `Time: ${h}:${String(b).padStart(2, '0')} ${a < 12 ? 'am' : 'pm'}`;
      } else right = `Score: ${s16(a)}  Moves: ${b}`;
      this.io.status(loc, right);
    }
    random(range) {
      range = s16(range);
      if (range <= 0) {
        // Negative seeds make the game predictable; zero goes back to true randomness.
        let seed = -range;
        this.rng = range < 0 ? () => {
          seed = (seed * 1103515245 + 12345) & 0x7fffffff;
          return seed / 0x80000000;
        } : null;
        return 0;
      }
      const r = this.rng ? this.rng() : Math.random();
      return 1 + Math.floor(r * range);
    }

    // ---- save state ----
    snapshot() {
      const dyn = this.mem.subarray(0, this.staticBase);
      let bin = '';
      for (let i = 0; i < dyn.length; i++) bin += String.fromCharCode(dyn[i]);
      const copy = (f) => ({ locals: f.locals.slice(), retPC: f.retPC, storeVar: f.storeVar, stackBase: f.stackBase });
      return {
        release: this.release,
        serial: this.serial,
        pc: this.pc,
        mem: btoa(bin),
        stack: this.stack.slice(),
        frames: this.frames.map(copy),
        frame: copy(this.frame),
        waiting: this.waiting ? { ...this.waiting } : null,
      };
    }
    load(snap) {
      if (snap.release !== this.release || snap.serial !== this.serial) {
        throw new Error('That save is from a different game');
      }
      const bin = atob(snap.mem);
      const flags2 = this.mem[0x11];
      this.mem.set(this.story);
      for (let i = 0; i < bin.length; i++) this.mem[i] = bin.charCodeAt(i);
      this.mem[0x11] = flags2;
      this.mem[1] &= ~0x30;
      this.pc = snap.pc;
      this.stack = snap.stack.slice();
      this.frames = snap.frames.map((f) => ({ ...f, locals: f.locals.slice() }));
      this.frame = { ...snap.frame, locals: snap.frame.locals.slice() };
      this.waiting = snap.waiting ? { ...snap.waiting } : null;
      this.memStreams = [];
      this.halted = false;
    }

    // ---- input ----
    input(line) {
      if (!this.waiting) return;
      const { text, parse } = this.waiting;
      const max = Math.max(0, this.mem[text] - 1);
      const s = line.toLowerCase().slice(0, max);
      for (let i = 0; i < s.length; i++) {
        const c = s.charCodeAt(i);
        this.wb(text + 1 + i, c >= 32 && c <= 126 ? c : 32);
      }
      this.wb(text + 1 + s.length, 0);
      this.tokenize(text, parse);
      this.waiting = null;
      this.run();
    }

    run() {
      let budget = 5e6;
      while (!this.waiting && !this.halted) {
        if (--budget < 0) throw new Error('The game is stuck in a loop');
        this.step();
      }
    }

    step() {
      const op = this.mem[this.pc++];
      let kind, num;
      const ops = [];
      if (op < 0x80) {
        kind = 2;
        num = op & 0x1f;
        ops.push(this.operand(op & 0x40 ? 2 : 1), this.operand(op & 0x20 ? 2 : 1));
      } else if (op < 0xc0) {
        num = op & 0x0f;
        const t = (op >> 4) & 3;
        if (t === 3) kind = 0;
        else { kind = 1; ops.push(this.operand(t)); }
      } else {
        kind = op & 0x20 ? 3 : 2;
        num = op & 0x1f;
        const types = this.mem[this.pc++];
        for (let i = 0; i < 4; i++) {
          const t = (types >> (6 - 2 * i)) & 3;
          if (t === 3) break;
          ops.push(this.operand(t));
        }
      }
      const [a, b] = ops;
      if (kind === 2) this.op2(num, ops, a, b, op);
      else if (kind === 1) this.op1(num, a, op);
      else if (kind === 0) this.op0(num, op);
      else this.opVar(num, ops, a, b, op);
    }

    bad(op) { throw new Error(`Unknown opcode 0x${op.toString(16)} at 0x${(this.pc - 1).toString(16)}`); }

    op2(num, ops, a, b, op) {
      switch (num) {
        case 1: this.branch(ops.slice(1).includes(a)); break; // je
        case 2: this.branch(s16(a) < s16(b)); break; // jl
        case 3: this.branch(s16(a) > s16(b)); break; // jg
        case 4: { const v = (s16(this.peekVarRef(a)) - 1) & 0xffff; this.pokeVarRef(a, v); this.branch(s16(v) < s16(b)); break; } // dec_chk
        case 5: { const v = (s16(this.peekVarRef(a)) + 1) & 0xffff; this.pokeVarRef(a, v); this.branch(s16(v) > s16(b)); break; } // inc_chk
        case 6: this.branch(this.parent(a) === b); break; // jin
        case 7: this.branch((a & b) === b); break; // test
        case 8: this.store(a | b); break; // or
        case 9: this.store(a & b); break; // and
        case 10: this.branch(this.testAttr(a, b)); break; // test_attr
        case 11: this.setAttr(a, b, true); break; // set_attr
        case 12: this.setAttr(a, b, false); break; // clear_attr
        case 13: this.pokeVarRef(a, b); break; // store
        case 14: this.insertObj(a, b); break; // insert_obj
        case 15: this.store(this.rw((a + 2 * b) & 0xffff)); break; // loadw
        case 16: this.store(this.mem[(a + b) & 0xffff]); break; // loadb
        case 17: this.store(this.getProp(a, b)); break; // get_prop
        case 18: { const p = this.findProp(a, b); this.store(p ? p + 1 : 0); break; } // get_prop_addr
        case 19: this.store(this.nextProp(a, b)); break; // get_next_prop
        case 20: this.store(s16(a) + s16(b)); break; // add
        case 21: this.store(s16(a) - s16(b)); break; // sub
        case 22: this.store(Math.imul(s16(a), s16(b))); break; // mul
        case 23: if (!b) throw new Error('Division by zero'); this.store(Math.trunc(s16(a) / s16(b))); break; // div
        case 24: if (!b) throw new Error('Division by zero'); this.store(s16(a) % s16(b)); break; // mod
        default: this.bad(op);
      }
    }

    op1(num, a, op) {
      switch (num) {
        case 0: this.branch(a === 0); break; // jz
        case 1: { const s = this.sibling(a); this.store(s); this.branch(s); break; } // get_sibling
        case 2: { const c = this.child(a); this.store(c); this.branch(c); break; } // get_child
        case 3: this.store(this.parent(a)); break; // get_parent
        case 4: this.store(a ? (this.mem[a - 1] >> 5) + 1 : 0); break; // get_prop_len
        case 5: this.pokeVarRef(a, s16(this.peekVarRef(a)) + 1); break; // inc
        case 6: this.pokeVarRef(a, s16(this.peekVarRef(a)) - 1); break; // dec
        case 7: this.print(this.decode(a).text); break; // print_addr
        case 9: this.removeObj(a); break; // remove_obj
        case 10: this.print(this.objName(a)); break; // print_obj
        case 11: this.ret(a); break; // ret
        case 12: this.pc += s16(a) - 2; break; // jump
        case 13: this.print(this.decode(a * 2).text); break; // print_paddr
        case 14: this.store(this.peekVarRef(a)); break; // load
        case 15: this.store(~a); break; // not
        default: this.bad(op);
      }
    }

    op0(num, op) {
      switch (num) {
        case 0: this.ret(1); break; // rtrue
        case 1: this.ret(0); break; // rfalse
        case 2: case 3: { // print, print_ret
          const { text, end } = this.decode(this.pc);
          this.pc = end;
          this.print(text);
          if (num === 3) { this.print('\n'); this.ret(1); }
          break;
        }
        case 4: break; // nop
        case 5: { // save: the snapshot's pc sits on this instruction's branch data
          let ok = false;
          try { ok = !!this.io.save(this.snapshot()); } catch (e) { ok = false; }
          this.branch(ok);
          break;
        }
        case 6: { // restore
          let snap = null;
          try { snap = this.io.restore(); } catch (e) { snap = null; }
          if (!snap) { this.branch(false); break; }
          try { this.load(snap); } catch (e) { this.print(`[${e.message}]\n`); this.branch(false); break; }
          this.branch(true);
          break;
        }
        case 7: { // restart
          const flags2 = this.mem[0x11];
          this.reset();
          this.mem[0x11] = flags2;
          if (this.io.restart) this.io.restart();
          break;
        }
        case 8: this.ret(this.stack.pop()); break; // ret_popped
        case 9: this.stack.pop(); break; // pop
        case 10: this.halted = true; if (this.io.quit) this.io.quit(); break; // quit
        case 11: this.print('\n'); break; // new_line
        case 12: this.updateStatus(); break; // show_status
        case 13: { // verify
          const len = this.rw(0x1a) * 2;
          let sum = 0;
          for (let i = 0x40; i < len && i < this.story.length; i++) sum = (sum + this.story[i]) & 0xffff;
          this.branch(sum === this.rw(0x1c));
          break;
        }
        case 15: this.branch(true); break; // piracy
        default: this.bad(op);
      }
    }

    opVar(num, ops, a, b, op) {
      switch (num) {
        case 0: { const sv = this.mem[this.pc++]; this.call(a, ops.slice(1), sv); break; } // call
        case 1: this.ww((a + 2 * b) & 0xffff, ops[2]); break; // storew
        case 2: this.wb((a + b) & 0xffff, ops[2]); break; // storeb
        case 3: this.putProp(a, b, ops[2]); break; // put_prop
        case 4: this.updateStatus(); this.waiting = { text: a, parse: b }; break; // sread
        case 5: this.print(this.zscii(a)); break; // print_char
        case 6: this.print(String(s16(a))); break; // print_num
        case 7: this.store(this.random(a)); break; // random
        case 8: this.writeVar(0, a); break; // push
        case 9: { const v = this.stack.pop(); this.pokeVarRef(a, v); break; } // pull
        case 10: case 11: case 20: case 21: break; // split_window, set_window, input_stream, sound_effect
        case 19: { // output_stream
          const n = s16(a);
          if (n === 3) this.memStreams.push({ table: b, len: 0 });
          else if (n === -3 && this.memStreams.length) {
            const s = this.memStreams.pop();
            this.ww(s.table, s.len);
          }
          break;
        }
        default: this.bad(op);
      }
    }
  }

  root.ZMachine = ZMachine;
  if (typeof module !== 'undefined') module.exports = ZMachine;
})(typeof globalThis !== 'undefined' ? globalThis : this);
