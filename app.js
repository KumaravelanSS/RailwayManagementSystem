/**
 * IRCTC NextGen Passenger Reservation Engine & Web Portal
 * Custom DSA Architecture:
 *   - Separate-Chaining Prime-Bucket Hash Maps (O(1) amortized lookup)
 *   - 2-D Segment Boolean Bitmap Matrix (Multi-hop seat availability & allocation)
 *   - Singly Linked List Confirmed Passenger Roster (Head/Tail pointers)
 *   - FIFO Waitlist Pointer Queue (Front/Rear cursors with cascade promotion)
 *   - Geographic Indian Railways Rail Corridor & Path Validation Engine
 *   - Multi-Option Digital Payments with RBI Tokenization Security
 */

// =============================================================================
// SECTION 1: INDIAN RAILWAYS NETWORK CORRIDORS & GEOGRAPHIC GRAPH
// =============================================================================

const RAILWAY_NETWORK = {
  stations: {
    "NDLS": { code: "NDLS", name: "New Delhi", city: "Delhi", state: "Delhi" },
    "MTJ":  { code: "MTJ",  name: "Mathura Jn", city: "Mathura", state: "Uttar Pradesh" },
    "KOTA": { code: "KOTA", name: "Kota Jn", city: "Kota", state: "Rajasthan" },
    "RTM":  { code: "RTM",  name: "Ratlam Jn", city: "Ratlam", state: "Madhya Pradesh" },
    "BRC":  { code: "BRC",  name: "Vadodara Jn", city: "Vadodara", state: "Gujarat" },
    "ST":   { code: "ST",   name: "Surat", city: "Surat", state: "Gujarat" },
    "MMCT": { code: "MMCT", name: "Mumbai Central", city: "Mumbai", state: "Maharashtra" },
    "CSMT": { code: "CSMT", name: "Mumbai CSMT", city: "Mumbai", state: "Maharashtra" },
    "AGC":  { code: "AGC",  name: "Agra Cantt", city: "Agra", state: "Uttar Pradesh" },
    "GWL":  { code: "GWL",  name: "Gwalior Jn", city: "Gwalior", state: "Madhya Pradesh" },
    "VGLJ": { code: "VGLJ", name: "VGL Jhansi", city: "Jhansi", state: "Uttar Pradesh" },
    "BPL":  { code: "BPL",  name: "Bhopal Jn", city: "Bhopal", state: "Madhya Pradesh" },
    "ET":   { code: "ET",   name: "Itarsi Jn", city: "Itarsi", state: "Madhya Pradesh" },
    "BSL":  { code: "BSL",  name: "Bhusawal Jn", city: "Bhusawal", state: "Maharashtra" },
    "NK":   { code: "NK",   name: "Nashik Road", city: "Nashik", state: "Maharashtra" },
    "NGP":  { code: "NGP",  name: "Nagpur Jn", city: "Nagpur", state: "Maharashtra" },
    "BPQ":  { code: "BPQ",  name: "Balharshah Jn", city: "Balharshah", state: "Maharashtra" },
    "WL":   { code: "WL",   name: "Warangal", city: "Warangal", state: "Telangana" },
    "BZA":  { code: "BZA",  name: "Vijayawada Jn", city: "Vijayawada", state: "Andhra Pradesh" },
    "MAS":  { code: "MAS",  name: "Chennai Central", city: "Chennai", state: "Tamil Nadu" },
    "ALJN": { code: "ALJN", name: "Aligarh Jn", city: "Aligarh", state: "Uttar Pradesh" },
    "CNB":  { code: "CNB",  name: "Kanpur Central", city: "Kanpur", state: "Uttar Pradesh" },
    "PRYJ": { code: "PRYJ", name: "Prayagraj Jn", city: "Prayagraj", state: "Uttar Pradesh" },
    "DDU":  { code: "DDU",  name: "Pt. Deen Dayal Upadhyaya", city: "Mughalsarai", state: "Uttar Pradesh" },
    "PNBE": { code: "PNBE", name: "Patna Jn", city: "Patna", state: "Bihar" },
    "GAYA": { code: "GAYA", name: "Gaya Jn", city: "Gaya", state: "Bihar" },
    "ASN":  { code: "ASN",  name: "Asansol Jn", city: "Asansol", state: "West Bengal" },
    "HWH":  { code: "HWH",  name: "Howrah Jn", city: "Kolkata", state: "West Bengal" },
    "PUNE": { code: "PUNE", name: "Pune Jn", city: "Pune", state: "Maharashtra" },
    "DD":   { code: "DD",   name: "Daund Jn", city: "Daund", state: "Maharashtra" },
    "SUR":  { code: "SUR",  name: "Solapur", city: "Solapur", state: "Maharashtra" },
    "KLBG": { code: "KLBG", name: "Kalaburagi (Gulbarga)", city: "Kalaburagi", state: "Karnataka" },
    "WADI": { code: "WADI", name: "Wadi Jn", city: "Wadi", state: "Karnataka" },
    "GTL":  { code: "GTL",  name: "Guntakal Jn", city: "Guntakal", state: "Andhra Pradesh" },
    "RU":   { code: "RU",   name: "Renigunta Jn (Tirupati)", city: "Tirupati", state: "Andhra Pradesh" },
    "AJJ":  { code: "AJJ",  name: "Arakkonam Jn", city: "Arakkonam", state: "Tamil Nadu" },
    "KPD":  { code: "KPD",  name: "Katpadi Jn", city: "Vellore", state: "Tamil Nadu" },
    "JTJ":  { code: "JTJ",  name: "Jolarpettai Jn", city: "Jolarpettai", state: "Tamil Nadu" },
    "KJM":  { code: "KJM",  name: "Krishnarajapuram", city: "Bengaluru", state: "Karnataka" },
    "SBC":  { code: "SBC",  name: "KSR Bengaluru", city: "Bengaluru", state: "Karnataka" },
    "ADI":  { code: "ADI",  name: "Ahmedabad Jn", city: "Ahmedabad", state: "Gujarat" },
    "ANND": { code: "ANND", name: "Anand Jn", city: "Anand", state: "Gujarat" },
    "KGP":  { code: "KGP",  name: "Kharagpur Jn", city: "Kharagpur", state: "West Bengal" },
    "BBS":  { code: "BBS",  name: "Bhubaneswar", city: "Bhubaneswar", state: "Odisha" },
    "BAM":  { code: "BAM",  name: "Brahmapur", city: "Brahmapur", state: "Odisha" },
    "VSKP": { code: "VSKP", name: "Visakhapatnam", city: "Visakhapatnam", state: "Andhra Pradesh" },
    "RJY":  { code: "RJY",  name: "Rajahmundry", city: "Rajahmundry", state: "Andhra Pradesh" }
  },

  corridors: [
    {
      id: "DELHI_MUMBAI_WESTERN",
      name: "Delhi - Mumbai Western Trunk Corridor (via Kota / Vadodara)",
      stations: ["NDLS", "MTJ", "KOTA", "RTM", "BRC", "ST", "MMCT"]
    },
    {
      id: "DELHI_MUMBAI_CENTRAL",
      name: "Delhi - Mumbai Central Trunk Corridor (via Bhopal / Bhusawal)",
      stations: ["NDLS", "AGC", "GWL", "VGLJ", "BPL", "ET", "BSL", "NK", "CSMT"]
    },
    {
      id: "DELHI_HOWRAH_MAIN",
      name: "Delhi - Howrah Eastern Trunk Corridor (via Kanpur / Patna)",
      stations: ["NDLS", "ALJN", "CNB", "PRYJ", "DDU", "PNBE", "GAYA", "ASN", "HWH"]
    },
    {
      id: "DELHI_CHENNAI_GT",
      name: "Grand Trunk Corridor (Delhi - Chennai via Nagpur / Vijayawada)",
      stations: ["NDLS", "AGC", "GWL", "VGLJ", "BPL", "ET", "NGP", "BPQ", "WL", "BZA", "MAS"]
    },
    {
      id: "MUMBAI_CHENNAI_SOUTHERN",
      name: "Mumbai - Chennai Southern Trunk Corridor (via Pune / Guntakal)",
      stations: ["MMCT", "PUNE", "DD", "SUR", "KLBG", "WADI", "GTL", "RU", "MAS"]
    },
    {
      id: "HOWRAH_CHENNAI_EAST_COAST",
      name: "Howrah - Chennai East Coast Corridor (via Bhubaneswar / Vizag)",
      stations: ["HWH", "KGP", "BBS", "BAM", "VSKP", "RJY", "BZA", "MAS"]
    },
    {
      id: "CHENNAI_BANGALORE_LINE",
      name: "Chennai - Bengaluru Intercity Corridor",
      stations: ["MAS", "AJJ", "KPD", "JTJ", "KJM", "SBC"]
    },
    {
      id: "MUMBAI_AHMEDABAD_CORRIDOR",
      name: "Mumbai - Ahmedabad High-Density Corridor",
      stations: ["MMCT", "ST", "BRC", "ANND", "ADI"]
    }
  ]
};

// =============================================================================
// SECTION 2: GEOGRAPHIC RAIL CORRIDOR VALIDATION ENGINE
// =============================================================================

class RailCorridorValidator {
  /**
   * Finds all corridors that contain both origin and terminus
   */
  static findMatchingCorridors(originCode, destCode) {
    const o = originCode.toUpperCase();
    const d = destCode.toUpperCase();
    
    return RAILWAY_NETWORK.corridors.filter(corridor => {
      const idxO = corridor.stations.indexOf(o);
      const idxD = corridor.stations.indexOf(d);
      return idxO !== -1 && idxD !== -1;
    });
  }

  /**
   * Returns valid intermediate stations along the corridor between origin and dest
   */
  static getValidIntermediateStations(originCode, destCode) {
    const matching = this.findMatchingCorridors(originCode, destCode);
    const validStationsSet = new Set();

    matching.forEach(corridor => {
      const idxO = corridor.stations.indexOf(originCode);
      const idxD = corridor.stations.indexOf(destCode);
      const start = Math.min(idxO, idxD);
      const end = Math.max(idxO, idxD);

      for (let i = start + 1; i < end; i++) {
        validStationsSet.add(corridor.stations[i]);
      }
    });

    return Array.from(validStationsSet).map(code => RAILWAY_NETWORK.stations[code]).filter(Boolean);
  }

  /**
   * Validates if a proposed station can geographically exist on the route
   */
  static validateStopAddition(originCode, destCode, currentIntermediateCodes, newStopCode) {
    const o = originCode.toUpperCase();
    const d = destCode.toUpperCase();
    const n = newStopCode.toUpperCase();

    if (n === o || n === d) {
      return { valid: false, message: `Station ${n} is already designated as Origin or Terminus.` };
    }

    if (currentIntermediateCodes.includes(n)) {
      return { valid: false, message: `Station ${n} is already present in the intermediate stops list.` };
    }

    const matching = this.findMatchingCorridors(o, d);

    if (matching.length === 0) {
      // If cross-corridor route, verify network reachability
      const allKnown = Object.keys(RAILWAY_NETWORK.stations);
      if (!allKnown.includes(n)) {
        return { valid: false, message: `Station code ${n} does not exist in the Indian Railways network registry.` };
      }
      return { valid: true };
    }

    // Check if newStopCode is in any of the matching corridors
    const isStationInCorridor = matching.some(corridor => {
      const idxO = corridor.stations.indexOf(o);
      const idxD = corridor.stations.indexOf(d);
      const idxN = corridor.stations.indexOf(n);

      if (idxN === -1) return false;

      // Must be between origin and dest index
      const min = Math.min(idxO, idxD);
      const max = Math.max(idxO, idxD);
      return idxN > min && idxN < max;
    });

    if (!isStationInCorridor) {
      const originName = RAILWAY_NETWORK.stations[o]?.name || o;
      const destName = RAILWAY_NETWORK.stations[d]?.name || d;
      const newName = RAILWAY_NETWORK.stations[n]?.name || n;
      
      return {
        valid: false,
        message: `❌ GEOGRAPHIC VALIDATION REJECTION: "${newName} (${n})" cannot be an intermediate stop between "${originName}" and "${destName}"!\n` +
                 `It does not lie along the authentic Indian Railways rail corridor for this route. Valid stations are: ` +
                 this.getValidIntermediateStations(o, d).map(s => s.name).join(", ")
      };
    }

    // Directionality check (check that stops don't backtrack)
    const proposedChain = [o, ...currentIntermediateCodes, n, d];
    const hasOrderConflict = matching.some(corridor => {
      const indices = proposedChain.map(st => corridor.stations.indexOf(st));
      if (indices.some(idx => idx === -1)) return false;

      const isForward = indices[0] < indices[indices.length - 1];
      for (let i = 0; i < indices.length - 1; i++) {
        if (isForward && indices[i] >= indices[i + 1]) return true; // backtrack
        if (!isForward && indices[i] <= indices[i + 1]) return true;
      }
      return false;
    });

    if (hasOrderConflict && matching.length === 1) {
      return {
        valid: false,
        message: `❌ SEQUENTIAL ORDER ERROR: Adding ${n} at this position violates directional progress along the track (train cannot backtrack).`
      };
    }

    return { valid: true };
  }
}

// =============================================================================
// SECTION 3: CUSTOM DATA STRUCTURES (HASH MAP, MATRIX, LINKED LIST, FIFO QUEUE)
// =============================================================================

// Separate-Chaining Prime-Bucket Hash Map (O(1) Amortized)
class CustomHashMap {
  constructor(bucketCount = 1009) {
    this.bucketCount = bucketCount;
    this.buckets = new Array(bucketCount).fill(null).map(() => []);
    this.size = 0;
  }

  _hash(key) {
    let h = 0;
    const str = String(key);
    for (let i = 0; i < str.length; i++) {
      h = (h * 31 + str.charCodeAt(i)) % this.bucketCount;
    }
    return Math.abs(h);
  }

  put(key, value) {
    const idx = this._hash(key);
    const bucket = this.buckets[idx];
    for (let i = 0; i < bucket.length; i++) {
      if (bucket[i].key === key) {
        bucket[i].value = value;
        return;
      }
    }
    bucket.push({ key, value });
    this.size++;
  }

  get(key) {
    const idx = this._hash(key);
    const bucket = this.buckets[idx];
    for (let i = 0; i < bucket.length; i++) {
      if (bucket[i].key === key) return bucket[i].value;
    }
    return null;
  }

  remove(key) {
    const idx = this._hash(key);
    const bucket = this.buckets[idx];
    for (let i = 0; i < bucket.length; i++) {
      if (bucket[i].key === key) {
        const val = bucket[i].value;
        bucket.splice(i, 1);
        this.size--;
        return val;
      }
    }
    return null;
  }

  values() {
    const res = [];
    for (let i = 0; i < this.bucketCount; i++) {
      for (const item of this.buckets[i]) {
        res.push(item.value);
      }
    }
    return res;
  }
}

// Singly Linked List for Confirmed Passenger Roster (Head & Tail Tracking)
class PassengerLinkedList {
  constructor() {
    this.head = null;
    this.tail = null;
    this.count = 0;
  }

  insert(passenger) {
    const node = { ...passenger, next: null };
    if (!this.head) {
      this.head = this.tail = node;
    } else {
      this.tail.next = node;
      this.tail = node;
    }
    this.count++;
  }

  remove(pnr) {
    let curr = this.head;
    let prev = null;
    while (curr) {
      if (curr.pnr === pnr) {
        if (!prev) this.head = curr.next;
        else prev.next = curr.next;
        if (curr === this.tail) this.tail = prev;
        this.count--;
        return true;
      }
      prev = curr;
      curr = curr.next;
    }
    return false;
  }

  snapshot() {
    const list = [];
    let curr = this.head;
    while (curr) {
      list.push(curr);
      curr = curr.next;
    }
    return list;
  }
}

// FIFO Waitlist Queue with Front and Rear Cursors
class FIFOQueue {
  constructor() {
    this.front = null;
    this.rear = null;
    this.count = 0;
  }

  enqueue(passenger) {
    const node = { ...passenger, next: null };
    if (!this.rear) {
      this.front = this.rear = node;
    } else {
      this.rear.next = node;
      this.rear = node;
    }
    this.count++;
  }

  dequeue() {
    if (!this.front) return null;
    const node = this.front;
    this.front = this.front.next;
    if (!this.front) this.rear = null;
    this.count--;
    return node;
  }

  isEmpty() {
    return this.front === null;
  }

  snapshot() {
    const list = [];
    let curr = this.front;
    while (curr) {
      list.push(curr);
      curr = curr.next;
    }
    return list;
  }
}

// Train Instance containing 2-D Segment Inventory Bitmap Matrix
class TrainInstance {
  constructor(trainId, trainName, date, stations, seatCapacity = 4) {
    this.trainId = trainId;
    this.trainName = trainName;
    this.date = date;
    this.stations = stations;
    this.seatCapacity = seatCapacity;
    this.numSegments = stations.length - 1;
    this.baseRatePerHop = 150.0;

    // 2-D Segment Inventory Bitmap Matrix: [numSegments][seatCapacity]
    // false = FREE, true = OCCUPIED
    this.segmentInventory = Array.from({ length: this.numSegments }, () =>
      new Array(this.seatCapacity).fill(false)
    );

    this.confirmedRoster = new PassengerLinkedList();
    this.waitingList = new FIFOQueue();
  }

  getStationIndex(st) {
    const clean = st.trim().toUpperCase();
    for (let i = 0; i < this.stations.length; i++) {
      if (this.stations[i].toUpperCase() === clean || this.stations[i].includes(clean)) return i;
    }
    return -1;
  }

  computeFare(srcIdx, destIdx) {
    return (destIdx - srcIdx) * this.baseRatePerHop;
  }

  // Scans 2-D matrix for available seat across all requested hops
  checkAvailability(srcIdx, destIdx) {
    for (let seat = 0; seat < this.seatCapacity; seat++) {
      let isFree = true;
      for (let seg = srcIdx; seg < destIdx; seg++) {
        if (this.segmentInventory[seg][seat]) {
          isFree = false;
          break;
        }
      }
      if (isFree) return seat;
    }
    return -1;
  }

  countAvailableSeats(srcIdx, destIdx) {
    let count = 0;
    for (let seat = 0; seat < this.seatCapacity; seat++) {
      let isFree = true;
      for (let seg = srcIdx; seg < destIdx; seg++) {
        if (this.segmentInventory[seg][seat]) {
          isFree = false;
          break;
        }
      }
      if (isFree) count++;
    }
    return count;
  }

  isSeatAvailable(srcIdx, destIdx, seatNo) {
    if (seatNo < 0 || seatNo >= this.seatCapacity) return false;
    for (let seg = srcIdx; seg < destIdx; seg++) {
      if (this.segmentInventory[seg][seatNo]) return false;
    }
    return true;
  }

  getAvailableSeatIndices(srcIdx, destIdx) {
    const list = [];
    for (let seat = 0; seat < this.seatCapacity; seat++) {
      if (this.isSeatAvailable(srcIdx, destIdx, seat)) {
        list.push(seat);
      }
    }
    return list;
  }

  allocateSegments(srcIdx, destIdx, seatNo) {
    for (let seg = srcIdx; seg < destIdx; seg++) {
      this.segmentInventory[seg][seatNo] = true;
    }
  }

  deallocateSegments(srcIdx, destIdx, seatNo) {
    for (let seg = srcIdx; seg < destIdx; seg++) {
      this.segmentInventory[seg][seatNo] = false;
    }
  }

  getRouteChainDisplay() {
    return this.stations.join(" ➔ ");
  }
}

// Central Data Engine with Prime-Bucket Hash Maps
class CentralDataEngine {
  constructor() {
    this.userMap = new CustomHashMap(1009);
    this.pnrMap = new CustomHashMap(1009);
    this.routeMatrix = new CustomHashMap(1009);
    this.pnrSeed = 4290000000;
    this.cancellationFee = 60.0;

    this.initUsers();
    this.bootstrapTrains();
  }

  initUsers() {
    this.userMap.put("passenger1", {
      userId: "passenger1",
      password: "pass123",
      isAdmin: false,
      walletBalance: 10000.0,
      savedPayment: {
        type: "CARD",
        masked: "RuPay •••• •••• •••• 8821",
        holder: "RAHUL KUMAR",
        expiry: "08/28"
      }
    });

    this.userMap.put("passenger2", {
      userId: "passenger2",
      password: "pass123",
      isAdmin: false,
      walletBalance: 8500.0,
      savedPayment: null
    });

    this.userMap.put("admin@irctc.res.in", {
      userId: "admin@irctc.res.in",
      password: "adminRoot",
      isAdmin: true,
      walletBalance: 50000.0,
      savedPayment: null
    });
  }

  bootstrapTrains() {
    // 1. Delhi - Mumbai Western Trunk
    this.addTrain(new TrainInstance("12951", "TEJAS RAJDHANI EXP", "2026-11-01", 
      ["NDLS", "MTJ", "KOTA", "RTM", "BRC", "ST", "MMCT"], 4));

    this.addTrain(new TrainInstance("12953", "AUGUST KRANTI RAJDHANI", "2026-11-01", 
      ["NDLS", "MTJ", "KOTA", "RTM", "BRC", "ST", "MMCT"], 4));

    // 2. Delhi - Howrah Eastern Trunk
    this.addTrain(new TrainInstance("12302", "HOWRAH RAJDHANI EXP", "2026-11-01", 
      ["NDLS", "CNB", "PRYJ", "DDU", "PNBE", "ASN", "HWH"], 3));

    // 3. Delhi - Chennai Grand Trunk
    this.addTrain(new TrainInstance("12616", "GRAND TRUNK EXPRESS", "2026-11-01", 
      ["NDLS", "AGC", "GWL", "VGLJ", "BPL", "ET", "NGP", "BPQ", "WL", "BZA", "MAS"], 4));

    // 4. Mumbai - Chennai Southern Trunk
    this.addTrain(new TrainInstance("11041", "CSMT CHENNAI EXPRESS", "2026-11-01", 
      ["MMCT", "PUNE", "DD", "SUR", "KLBG", "WADI", "GTL", "RU", "MAS"], 5));

    // 5. Mumbai - Ahmedabad High Density
    this.addTrain(new TrainInstance("12009", "SHATABDI EXPRESS", "2026-11-01", 
      ["MMCT", "ST", "BRC", "ANND", "ADI"], 3));

    // 6. Chennai - Bangalore
    this.addTrain(new TrainInstance("12027", "CHENNAI SBC SHATABDI", "2026-11-01", 
      ["MAS", "KPD", "JTJ", "KJM", "SBC"], 4));
  }

  addTrain(trainInstance) {
    const stns = trainInstance.stations;
    // Multi-hop station pair permutations in Route Matrix
    for (let i = 0; i < stns.length; i++) {
      for (let j = i + 1; j < stns.length; j++) {
        const key = `${stns[i]}_${stns[j]}_${trainInstance.date}`.toUpperCase();
        let list = this.routeMatrix.get(key);
        if (!list) {
          list = [];
          this.routeMatrix.put(key, list);
        }
        if (!list.some(t => t.trainId === trainInstance.trainId)) {
          list.push(trainInstance);
        }
      }
    }
  }

  removeTrain(trainId, date = null) {
    let removed = false;
    for (let i = 0; i < this.routeMatrix.bucketCount; i++) {
      for (const item of this.routeMatrix.buckets[i]) {
        const list = item.value;
        const initialLen = list.length;
        item.value = list.filter(t => {
          const matchId = t.trainId === trainId;
          const matchDate = !date || t.date === date;
          return !(matchId && matchDate);
        });
        if (item.value.length < initialLen) removed = true;
      }
    }
    return removed;
  }

  getAllUniqueTrains() {
    const unique = new Map();
    for (let i = 0; i < this.routeMatrix.bucketCount; i++) {
      for (const item of this.routeMatrix.buckets[i]) {
        for (const t of item.value) {
          unique.set(`${t.trainId}_${t.date}`, t);
        }
      }
    }
    return Array.from(unique.values()).sort((a, b) => a.trainId.localeCompare(b.trainId));
  }

  findTrainInstance(trainId, date) {
    const all = this.getAllUniqueTrains();
    return all.find(t => t.trainId === trainId && (!date || t.date === date)) || null;
  }

  queryTrains(src, dest, date) {
    const key = `${src}_${dest}_${date}`.toUpperCase();
    return this.routeMatrix.get(key) || [];
  }

  bookTicket(user, trainInstance, src, dest, paymentMethod, paymentDetails = {}, customFare = null, passengerInfo = null) {
    const s = trainInstance.getStationIndex(src);
    const e = trainInstance.getStationIndex(dest);
    if (s < 0 || e < 0 || s >= e) {
      return { success: false, message: `Invalid route segment bounds: ${src} to ${dest}` };
    }

    const fare = (customFare !== null && customFare !== undefined) ? customFare : trainInstance.computeFare(s, e);

    // Verify & Deduct balance if Wallet
    if (paymentMethod === "WALLET") {
      if (user.walletBalance < fare) {
        return { success: false, message: `Insufficient wallet balance. Required: ₹${fare.toFixed(2)}, Available: ₹${user.walletBalance.toFixed(2)}` };
      }
      user.walletBalance -= fare;
    } else {
      // Digital simulation deduction to keep ledger synchronized
      if (user.walletBalance >= fare) user.walletBalance -= fare;
    }

    const pnr = `PNR${++this.pnrSeed}`;

    // Check if user requested a specific seat that is available
    let seat = -1;
    if (passengerInfo && passengerInfo.specificSeat !== null && passengerInfo.specificSeat !== undefined && passengerInfo.specificSeat !== -1) {
      if (trainInstance.isSeatAvailable(s, e, passengerInfo.specificSeat)) {
        seat = passengerInfo.specificSeat;
      }
    }
    // If not specific seat or specific seat was taken, check first available seat
    if (seat === -1) {
      seat = trainInstance.checkAvailability(s, e);
    }

    const pName = passengerInfo?.name || user.userId;
    const pAge = passengerInfo?.age || 28;
    const pGender = passengerInfo?.gender || "MALE";
    const pClass = passengerInfo?.classCode || "3A";
    const pQuota = passengerInfo?.quota || "GN";

    const coachMap = { "1A": "H1", "2A": "A1", "3A": "B1", "SL": "S1", "2S": "D1" };
    const coach = coachMap[pClass] || "B1";

    const passenger = {
      pnr,
      userId: user.userId,
      passengerName: pName,
      passengerAge: pAge,
      passengerGender: pGender,
      passengerMobile: passengerInfo?.mobile || "9876543210",
      berthPreference: passengerInfo?.berthPref || "NO_PREF",
      selectedClass: pClass,
      quota: pQuota,
      trainId: trainInstance.trainId,
      trainName: trainInstance.trainName,
      date: trainInstance.date,
      src,
      dest,
      farePaid: fare,
      paymentMethod,
      paymentDetails,
      bookingTimestamp: Date.now()
    };

    if (seat !== -1) {
      passenger.status = "CONFIRMED";
      passenger.assignedSeat = seat;
      passenger.coach = coach;
      passenger.berth = getBerthTitle(seat, pClass);
      trainInstance.allocateSegments(s, e, seat);
      trainInstance.confirmedRoster.insert(passenger);
      this.pnrMap.put(pnr, passenger);
      return { success: true, status: "CONFIRMED", pnr, passenger };
    } else {
      passenger.status = "WAITING";
      passenger.assignedSeat = -1;
      passenger.coach = "--";
      passenger.berth = "--";
      const wlPos = trainInstance.waitingList.count + 1;
      passenger.waitlistPosition = wlPos;
      trainInstance.waitingList.enqueue(passenger);
      this.pnrMap.put(pnr, passenger);
      return { success: true, status: "WAITING", pnr, passenger, waitlistPosition: wlPos };
    }
  }

  cancelTicket(pnr) {
    const passenger = this.pnrMap.get(pnr);
    if (!passenger) return { success: false, message: `PNR not found: ${pnr}` };
    if (passenger.status === "CANCELLED") return { success: false, message: "Ticket is already cancelled." };

    const train = this.findTrainInstance(passenger.trainId, passenger.date);
    if (!train) return { success: false, message: "Train instance routing mismatch." };

    const refund = Math.max(0, passenger.farePaid - this.cancellationFee);
    const user = this.userMap.get(passenger.userId);
    if (user) user.walletBalance += refund;

    const s = train.getStationIndex(passenger.src);
    const e = train.getStationIndex(passenger.dest);

    let promotionMessage = "";

    if (passenger.status === "CONFIRMED") {
      train.deallocateSegments(s, e, passenger.assignedSeat);
      train.confirmedRoster.remove(pnr);
      passenger.status = "CANCELLED";

      // Cascade promotion from FIFO Queue
      if (!train.waitingList.isEmpty()) {
        const tempQueue = new FIFOQueue();
        let promoted = false;

        while (!train.waitingList.isEmpty()) {
          const waitPassenger = train.waitingList.dequeue();
          const ws = train.getStationIndex(waitPassenger.src);
          const we = train.getStationIndex(waitPassenger.dest);

          if (!promoted && waitPassenger.status === "WAITING") {
            const availSeat = train.checkAvailability(ws, we);
            if (availSeat !== -1) {
              waitPassenger.status = "CONFIRMED";
              waitPassenger.assignedSeat = availSeat;
              waitPassenger.coach = "B1";
              waitPassenger.berth = getBerthTitle(availSeat, waitPassenger.selectedClass || "3A");
              train.allocateSegments(ws, we, availSeat);
              train.confirmedRoster.insert(waitPassenger);
              this.pnrMap.put(waitPassenger.pnr, waitPassenger);
              promotionMessage = `Waitlisted PNR ${waitPassenger.pnr} has been auto-promoted to Coach B1 / Seat #${availSeat + 1}!`;
              promoted = true;
              continue;
            }
          }
          tempQueue.enqueue(waitPassenger);
        }
        train.waitingList = tempQueue;
      }
    } else {
      // Remove from FIFO waitlist queue
      const tempQueue = new FIFOQueue();
      while (!train.waitingList.isEmpty()) {
        const w = train.waitingList.dequeue();
        if (w.pnr !== pnr) tempQueue.enqueue(w);
      }
      train.waitingList = tempQueue;
      passenger.status = "CANCELLED";
    }

    return {
      success: true,
      pnr,
      refund,
      deduction: this.cancellationFee,
      promotionMessage
    };
  }

  getUserBookings(userId) {
    const list = [];
    for (let i = 0; i < this.pnrMap.bucketCount; i++) {
      for (const item of this.pnrMap.buckets[i]) {
        if (item.value.userId === userId) {
          list.push(item.value);
        }
      }
    }
    return list.sort((a, b) => b.bookingTimestamp - a.bookingTimestamp);
  }
}

// =============================================================================
// SECTION 4: GLOBAL STATE & UI CONTROLLERS
// =============================================================================

const engine = new CentralDataEngine();
let currentUser = engine.userMap.get("passenger1"); // Initialized as passenger

// Current temporary route builder state
let builderIntermediateStops = ["KOTA", "RTM", "BRC", "ST"];
let editingTrainId = null;
let editingTrainDate = null;

// Temporary booking context
let activeBookingContext = null;

// Initialize on DOM Load
document.addEventListener("DOMContentLoaded", () => {
  initClock();
  initStationDatalist();
  updateUserHeader();
  renderNavigation();
  initAdminCorridorBuilder();
  renderActiveTrainsDirectory();
  populateDsaTrainDropdown();

  // Trigger initial search
  executeSearchTrains("NDLS", "MMCT", "2026-11-01");
});

function initClock() {
  const clockEl = document.getElementById("liveClock");
  setInterval(() => {
    const now = new Date();
    clockEl.textContent = now.toLocaleDateString("en-IN", {
      day: "2-digit", month: "short", year: "numeric"
    }) + " | " + now.toLocaleTimeString("en-IN");
  }, 1000);
}

function initStationDatalist() {
  const datalist = document.getElementById("stationList");
  datalist.innerHTML = "";
  Object.values(RAILWAY_NETWORK.stations).forEach(st => {
    const opt = document.createElement("option");
    opt.value = `${st.code} - ${st.name}`;
    datalist.appendChild(opt);
  });
}

function updateUserHeader() {
  const nameEl = document.getElementById("userNameDisplay");
  const roleEl = document.getElementById("userRolePill");
  const walletEl = document.getElementById("walletAmount");

  if (currentUser) {
    nameEl.textContent = currentUser.userId;
    walletEl.textContent = `₹ ${currentUser.walletBalance.toFixed(2)}`;

    if (currentUser.isAdmin) {
      roleEl.textContent = "ADMIN OFFICER";
      roleEl.className = "role-pill admin";
    } else {
      roleEl.textContent = "PASSENGER";
      roleEl.className = "role-pill passenger";
    }
  }
}

// Render dynamic navigation strictly separating Passenger vs Admin privileges
function renderNavigation() {
  const container = document.getElementById("navLinksContainer");
  container.innerHTML = "";

  const createNavBtn = (id, icon, text, isSpecial = false) => {
    const btn = document.createElement("button");
    btn.className = `nav-item ${isSpecial ? "admin-highlight" : ""}`;
    btn.id = `navBtn-${id}`;
    btn.innerHTML = `<span>${icon}</span> <span>${text}</span>`;
    btn.onclick = () => switchTab(id);
    return btn;
  };

  if (currentUser && currentUser.isAdmin) {
    // Admin gets Train Management & Live Engine first
    container.appendChild(createNavBtn("tab-admin", "⚙️", "TRAIN MANAGEMENT (Admin: Add/Edit/Remove)", true));
    container.appendChild(createNavBtn("tab-dsa", "🧬", "DSA LIVE MATRIX & ROSTER"));
    container.appendChild(createNavBtn("tab-book", "🚆", "BOOK TICKET"));
    container.appendChild(createNavBtn("tab-pnr", "🔍", "PNR ENQUIRY"));
    container.appendChild(createNavBtn("tab-bookings", "📋", "MY BOOKINGS"));
  } else {
    // Passenger ONLY gets public booking tabs (NO Train Management access)
    container.appendChild(createNavBtn("tab-book", "🚆", "BOOK TICKET"));
    container.appendChild(createNavBtn("tab-pnr", "🔍", "PNR ENQUIRY"));
    container.appendChild(createNavBtn("tab-bookings", "📋", "MY BOOKINGS"));
    container.appendChild(createNavBtn("tab-dsa", "🧬", "DSA LIVE ENGINE & MATRIX"));
  }

  // Set active class
  const activeTab = document.querySelector(".tab-view.active");
  if (activeTab) {
    const activeBtn = document.getElementById(`navBtn-${activeTab.id}`);
    if (activeBtn) activeBtn.classList.add("active");
  }
}

function switchTab(tabId) {
  // Privilege guard: if not admin, reject access to admin tab
  if (tabId === "tab-admin" && (!currentUser || !currentUser.isAdmin)) {
    showToast("Access Denied: Train Management is restricted to Railway Admin Officers.");
    tabId = "tab-book";
  }

  document.querySelectorAll(".tab-view").forEach(el => el.classList.remove("active"));
  document.querySelectorAll(".nav-item").forEach(el => el.classList.remove("active"));

  const targetTab = document.getElementById(tabId);
  const targetNav = document.getElementById(`navBtn-${tabId}`);
  if (targetTab) targetTab.classList.add("active");
  if (targetNav) targetNav.classList.add("active");

  // Tab-specific refreshes
  if (tabId === "tab-bookings") renderMyBookings();
  if (tabId === "tab-admin") renderActiveTrainsDirectory();
  if (tabId === "tab-dsa") renderDsaVisualizer();
}

// =============================================================================
// SEARCH TRAINS & RESULT CARDS
// =============================================================================

function swapSearchStations() {
  const fromEl = document.getElementById("searchFromStation");
  const toEl = document.getElementById("searchToStation");
  const tmp = fromEl.value;
  fromEl.value = toEl.value;
  toEl.value = tmp;
}

function quickFillRoute(from, to) {
  document.getElementById("searchFromStation").value = from;
  document.getElementById("searchToStation").value = to;
  handleSearchTrains(new Event("submit"));
}

function parseStationCode(inputVal) {
  if (!inputVal) return "";
  const match = inputVal.match(/^([A-Z]{2,5})\b/);
  return match ? match[1] : inputVal.trim().toUpperCase();
}

function getBerthTitle(seatIndex, classCode) {
  if (seatIndex < 0) return "--";
  if (classCode === "2S") {
    const types = ["WINDOW", "MIDDLE", "AISLE", "WINDOW"];
    return types[seatIndex % types.length] + ` (Seat ${seatIndex + 1})`;
  }
  const types = ["LOWER BERTH", "MIDDLE BERTH", "UPPER BERTH", "SIDE LOWER"];
  return types[seatIndex % types.length] + ` (Berth ${seatIndex + 1})`;
}

// Collapsible Search Bar Toggle
function toggleSearchForm() {
  const form = document.getElementById("searchTrainForm");
  const btnText = document.getElementById("btnToggleSearchText");
  if (!form || !btnText) return;

  if (form.style.display === "none") {
    form.style.display = "block";
    btnText.textContent = "Collapse Search ▴";
  } else {
    form.style.display = "none";
    btnText.textContent = "Modify Search ▾";
  }
}

// Tatkal Quota Rules & Window Notice
function onQuotaChanged() {
  const quota = document.getElementById("searchQuota").value;
  const strip = document.getElementById("tatkalInfoStrip");
  const infoText = document.getElementById("tatkalInfoText");
  const dateVal = document.getElementById("searchJourneyDate").value;

  if (quota === "TQ") {
    strip.style.display = "flex";
    const today = new Date("2026-10-29T00:00:00");
    const jDate = new Date(dateVal + "T00:00:00");
    const diffDays = Math.round((jDate - today) / (1000 * 60 * 60 * 24));

    if (diffDays === 1) {
      infoText.innerHTML = `<strong>⚡ Tatkal Window is OPEN:</strong> Journey date (${dateVal}) is tomorrow. Tatkal booking is officially open! (AC: 10:00 AM, Non-AC: 11:00 AM).`;
    } else if (diffDays <= 0) {
      infoText.innerHTML = `<strong>⚡ Same-Day / Current Tatkal:</strong> Booking allowed subject to chart finalization.`;
    } else {
      infoText.innerHTML = `<strong>⚠️ Tatkal Notice:</strong> Tatkal bookings strictly open <strong>1 day prior</strong> to travel date. For ${dateVal}, Tatkal opens on ${new Date(jDate.getTime() - 86400000).toISOString().split("T")[0]}. Toggle demo simulation on the right to test booking now!`;
    }
  } else {
    strip.style.display = "none";
  }

  // Refresh train listing if stations are filled
  const fromVal = document.getElementById("searchFromStation").value;
  const toVal = document.getElementById("searchToStation").value;
  const src = parseStationCode(fromVal);
  const dest = parseStationCode(toVal);
  if (src && dest && src !== dest) {
    executeSearchTrains(src, dest, dateVal);
  }
}

function onTatkalSimChanged() {
  const dateVal = document.getElementById("searchJourneyDate").value;
  const fromVal = document.getElementById("searchFromStation").value;
  const toVal = document.getElementById("searchToStation").value;
  const src = parseStationCode(fromVal);
  const dest = parseStationCode(toVal);
  if (src && dest && src !== dest) {
    executeSearchTrains(src, dest, dateVal);
  }
}

function handleSearchTrains(e) {
  if (e && e.preventDefault) e.preventDefault();
  const fromVal = document.getElementById("searchFromStation").value;
  const toVal = document.getElementById("searchToStation").value;
  const dateVal = document.getElementById("searchJourneyDate").value;

  const srcCode = parseStationCode(fromVal);
  const destCode = parseStationCode(toVal);

  if (!srcCode || !destCode) {
    showToast("Please enter valid Origin and Destination stations!");
    return;
  }

  if (srcCode === destCode) {
    showToast("Origin and Destination stations cannot be identical!");
    return;
  }

  // Validate rail corridor connection
  const validation = RailCorridorValidator.validateCorridor(srcCode, destCode);
  if (!validation.valid) {
    showToast(`⚠️ Route Notice: ${validation.message}`);
  }

  executeSearchTrains(srcCode, destCode, dateVal);
}

// Inline Segment Hops Drawer Toggle
function toggleHopsDropdown(trainId) {
  const drawer = document.getElementById(`hopsDrawer_${trainId}`);
  if (!drawer) return;
  drawer.classList.toggle("open");
}

// Interactive Class Selection inside Train Card
function selectTrainClass(trainId, classCode, fare, availText, isLocked) {
  const card = document.getElementById(`trainCard_${trainId}`);
  if (!card) return;

  card.querySelectorAll(".class-avail-box").forEach(b => b.classList.remove("selected"));
  const targetBox = document.getElementById(`cab_${trainId}_${classCode}`);
  if (targetBox) targetBox.classList.add("selected");

  card.dataset.selectedClass = classCode;
  card.dataset.selectedFare = fare;
  card.dataset.isLocked = isLocked ? "true" : "false";

  const bookBtn = document.getElementById(`bookBtn_${trainId}`);
  if (bookBtn) {
    if (isLocked) {
      bookBtn.disabled = true;
      bookBtn.innerHTML = `🔒 TATKAL LOCKED (Opens 1 Day Prior)`;
      bookBtn.className = "btn btn-outline btn-lg";
    } else {
      bookBtn.disabled = false;
      bookBtn.innerHTML = `BOOK ${classCode} (₹ ${fare.toFixed(2)}) ➔`;
      bookBtn.className = "btn btn-primary btn-lg";
    }
  }
}

function executeSearchTrains(srcCode, destCode, date) {
  const container = document.getElementById("trainCardsContainer");
  const countTitle = document.getElementById("resultsCountTitle");
  const subTitle = document.getElementById("resultsSubTitle");
  container.innerHTML = "";

  const srcStation = RAILWAY_NETWORK.stations[srcCode] || { name: srcCode };
  const destStation = RAILWAY_NETWORK.stations[destCode] || { name: destCode };

  const quotaVal = document.getElementById("searchQuota").value;
  const quotaText = document.getElementById("searchQuota").selectedOptions[0]?.text || "GENERAL (GN)";
  const classVal = document.getElementById("searchClass").value;
  const classText = document.getElementById("searchClass").selectedOptions[0]?.text || "All Classes";

  // Check Tatkal Window Rules
  const today = new Date("2026-10-29T00:00:00");
  const jDate = new Date(date + "T00:00:00");
  const diffDays = Math.round((jDate - today) / (1000 * 60 * 60 * 24));
  const simOpen = document.getElementById("simulateTatkalWindowOpen")?.checked ?? true;
  const isTatkal = quotaVal === "TQ";
  const isTatkalWindowOpen = (!isTatkal) || (diffDays <= 1) || simOpen;

  // Update Collapsible Search Summary Bar & Minimize Search Form Container
  const summaryBar = document.getElementById("searchSummaryBar");
  if (summaryBar) {
    document.getElementById("ssbOrigin").textContent = srcCode;
    document.getElementById("ssbDest").textContent = destCode;
    document.getElementById("ssbDate").textContent = date;
    document.getElementById("ssbQuota").textContent = quotaText;
    document.getElementById("ssbClass").textContent = classText;
    summaryBar.style.display = "flex";
  }

  // Minimize main search form to prioritize train listing screen space
  const searchForm = document.getElementById("searchTrainForm");
  if (searchForm) {
    searchForm.style.display = "none";
  }
  const btnToggleText = document.getElementById("btnToggleSearchText");
  if (btnToggleText) {
    btnToggleText.textContent = "Modify Search ▾";
  }

  // Filter Pill
  const pillQuota = document.getElementById("pillActiveQuota");
  if (pillQuota) {
    pillQuota.textContent = `${quotaText.toUpperCase()} ${isTatkal ? (isTatkalWindowOpen ? "• WINDOW OPEN" : "• LOCKED") : ""}`;
  }

  const trains = engine.queryTrains(srcCode, destCode, date);
  countTitle.textContent = `${trains.length} Train(s) Found for ${srcStation.name} ➔ ${destStation.name}`;
  subTitle.textContent = `Journey Date: ${date} | Quota: ${quotaText} | DSA 2-D Matrix Pricing`;

  if (trains.length === 0) {
    container.innerHTML = `
      <div class="card" style="text-align: center; padding: 40px 20px;">
        <h3 style="color: var(--text-muted); margin-bottom: 8px;">No Direct or Multi-Hop Trains Found</h3>
        <p style="font-size: 13px; color: var(--text-muted);">There are no scheduled trains connecting ${srcStation.name} (${srcCode}) and ${destStation.name} (${destCode}) on ${date}.</p>
        <button class="btn btn-outline" style="margin-top: 16px;" onclick="quickFillRoute('NDLS - New Delhi', 'MMCT - Mumbai Central')">Try Popular Delhi ➔ Mumbai Corridor</button>
      </div>
    `;
    return;
  }

  trains.forEach(ti => {
    const s = ti.getStationIndex(srcCode);
    const e = ti.getStationIndex(destCode);
    const hops = (s >= 0 && e >= 0) ? (e - s) : 1;
    const baseFare = ti.computeFare(s, e);
    const availSeats = ti.countAvailableSeats(s, e);

    // Class Fares & Tatkal Surcharges
    const tatkalSurcharges = { "3A": 150, "SL": 100, "2S": 50 };
    const classPremiums = { "3A": 200, "SL": 0, "2S": -baseFare * 0.25 };

    const fare3A = baseFare + classPremiums["3A"] + (isTatkal ? tatkalSurcharges["3A"] : 0);
    const fareSL = baseFare + classPremiums["SL"] + (isTatkal ? tatkalSurcharges["SL"] : 0);
    const fare2S = Math.max(50, baseFare + classPremiums["2S"] + (isTatkal ? tatkalSurcharges["2S"] : 0));

    // Stoppages along the journey segment
    const segmentStops = ti.stations.slice(s, e + 1);

    const card = document.createElement("div");
    card.className = "train-card";
    card.id = `trainCard_${ti.trainId}`;
    card.dataset.selectedClass = "3A";
    card.dataset.selectedFare = fare3A;
    card.dataset.isLocked = (!isTatkalWindowOpen) ? "true" : "false";

    card.innerHTML = `
      <div class="tc-top">
        <div class="tc-train-info">
          <div class="tc-train-name">
            <span>🚆</span> <strong>${ti.trainId}</strong> | ${ti.trainName}
          </div>
          <div class="tc-runs-on">
            Runs Daily &bull; Total Segment Capacity: ${ti.seatCapacity} Seats/Seg
          </div>
        </div>
        <button class="btn btn-sm btn-outline" onclick="showTrainRouteModal('${ti.trainId}')">🗺️ View Route Schedule</button>
      </div>

      <!-- Emphasized Station Timings & Codes -->
      <div class="tc-route-info">
        <div class="station-time-box left">
          <div class="st-time">06:00</div>
          <span class="st-code-badge">${srcCode}</span>
          <div class="st-name">${srcStation.name}</div>
          <div class="st-date">${ti.date}</div>
        </div>

        <div class="route-arrow-box">
          <span class="rab-duration">${hops * 3}h 15m</span>
          <div class="rab-line"></div>
          <button type="button" class="rab-hops-btn" onclick="toggleHopsDropdown('${ti.trainId}')" title="Click to view all intermediate stops">
            ▾ ${hops} Segment Hop(s) (View Stops)
          </button>
        </div>

        <div class="station-time-box right">
          <div class="st-time">${(6 + hops * 3) % 24 < 10 ? "0" : ""}${(6 + hops * 3) % 24}:15</div>
          <span class="st-code-badge">${destCode}</span>
          <div class="st-name">${destStation.name}</div>
          <div class="st-date">${ti.date}</div>
        </div>
      </div>

      <!-- Inline Segment Hops Route Drawer -->
      <div class="tc-hops-drawer" id="hopsDrawer_${ti.trainId}">
        <div style="font-size:12px; font-weight:700; color:var(--irctc-navy); margin-bottom:8px;">
          🚉 Journey Stoppages (${segmentStops.length} Stations &bull; ${hops} Segment Hops):
        </div>
        <div class="hops-track-timeline">
          ${segmentStops.map((stCode, idx) => `
            <div class="htt-stop">
              <div class="htt-code">${stCode}</div>
              <div class="htt-dot" style="${idx === 0 || idx === segmentStops.length - 1 ? 'background: #0B2F5C; box-shadow: 0 0 0 2px #0B2F5C;' : ''}"></div>
              <div class="htt-time">${idx === 0 ? "Dep 06:00" : idx === segmentStops.length - 1 ? `Arr ${(6 + hops * 3) % 24}:15` : `Halt 5m`}</div>
            </div>
            ${idx < segmentStops.length - 1 ? '<div class="htt-line"></div>' : ''}
          `).join("")}
        </div>
      </div>

      <!-- Grouped Class Availability Boxes -->
      <div class="tc-classes-row">
        <div class="tc-classes-grouped">
          <!-- Class 3A -->
          <div class="class-avail-box selected" id="cab_${ti.trainId}_3A" onclick="selectTrainClass('${ti.trainId}', '3A', ${fare3A}, '${availSeats > 0 ? "AVAILABLE" : "WAITLIST"}', ${!isTatkalWindowOpen})">
            <div class="cab-header">
              <span class="cab-title">AC 3 TIER (3A)</span>
              <span class="cab-fare">₹ ${fare3A.toFixed(2)}</span>
            </div>
            <div class="cab-status ${!isTatkalWindowOpen ? 'locked' : (availSeats > 0 ? 'available' : 'waiting')}">
              ${!isTatkalWindowOpen ? 'LOCKED (1 Day Prior)' : (availSeats > 0 ? `AVAILABLE - ${availSeats}` : `WL - ${ti.waitingList.count + 1}`)}
            </div>
          </div>

          <!-- Class SL -->
          <div class="class-avail-box" id="cab_${ti.trainId}_SL" onclick="selectTrainClass('${ti.trainId}', 'SL', ${fareSL}, '${availSeats > 0 ? "AVAILABLE" : "WAITLIST"}', ${!isTatkalWindowOpen})">
            <div class="cab-header">
              <span class="cab-title">SLEEPER (SL)</span>
              <span class="cab-fare">₹ ${fareSL.toFixed(2)}</span>
            </div>
            <div class="cab-status ${!isTatkalWindowOpen ? 'locked' : (availSeats > 0 ? 'available' : 'waiting')}">
              ${!isTatkalWindowOpen ? 'LOCKED (1 Day Prior)' : (availSeats > 0 ? `AVAILABLE - ${availSeats}` : `WL - ${ti.waitingList.count + 1}`)}
            </div>
          </div>

          <!-- Class 2S -->
          <div class="class-avail-box" id="cab_${ti.trainId}_2S" onclick="selectTrainClass('${ti.trainId}', '2S', ${fare2S}, 'AVAILABLE', ${!isTatkalWindowOpen})">
            <div class="cab-header">
              <span class="cab-title">2S SITTING</span>
              <span class="cab-fare">₹ ${fare2S.toFixed(2)}</span>
            </div>
            <div class="cab-status ${!isTatkalWindowOpen ? 'locked' : 'available'}">
              ${!isTatkalWindowOpen ? 'LOCKED (1 Day Prior)' : `AVAILABLE - ${availSeats + 2}`}
            </div>
          </div>
        </div>

        <div class="tc-actions">
          <button class="btn btn-primary btn-lg" id="bookBtn_${ti.trainId}" onclick="handleBookTrainClick('${ti.trainId}', '${srcCode}', '${destCode}')" ${!isTatkalWindowOpen ? 'disabled style="background:#94A3B8; cursor:not-allowed;"' : ''}>
            ${!isTatkalWindowOpen ? '🔒 TATKAL LOCKED' : `BOOK 3A (₹ ${fare3A.toFixed(2)}) ➔`}
          </button>
        </div>
      </div>
    `;
    container.appendChild(card);
  });
}

function handleBookTrainClick(trainId, srcCode, destCode) {
  const card = document.getElementById(`trainCard_${trainId}`);
  if (!card) return;
  if (card.dataset.isLocked === "true") {
    showToast("⚠️ Tatkal booking opens strictly 1 day prior to travel date. Toggle demo simulation to proceed.");
    return;
  }
  const selectedClass = card.dataset.selectedClass || "3A";
  const selectedFare = parseFloat(card.dataset.selectedFare) || 650;
  openPassengerModal(trainId, selectedClass, selectedFare, srcCode, destCode);
}

function showTrainRouteModal(trainId) {
  const train = engine.getAllUniqueTrains().find(t => t.trainId === trainId);
  if (!train) return;
  alert(`Train ${train.trainId} - ${train.trainName}\nRoute Stations:\n` + train.stations.map((s, idx) => `${idx + 1}. ${RAILWAY_NETWORK.stations[s]?.name || s} (${s})`).join("\n"));
}

// =============================================================================
// STEP 3: PASSENGER DETAILS ENTRY MODAL WITH BERTH & SPECIFIC SEAT SELECTION
// =============================================================================

function openPassengerModal(trainId, classCode, fare, srcCode, destCode) {
  if (!currentUser) {
    openAuthModal();
    return;
  }

  const train = engine.findTrainInstance(trainId);
  if (!train) {
    showToast("Train schedule instance not found.");
    return;
  }

  const s = train.getStationIndex(srcCode);
  const e = train.getStationIndex(destCode);
  const hops = (s >= 0 && e >= 0) ? (e - s) : 1;
  const quota = document.getElementById("searchQuota").value;

  activeBookingContext = {
    train,
    classCode,
    fare,
    srcCode,
    destCode,
    hops,
    quota,
    classPremium: classCode === "3A" ? 200 : (classCode === "2S" ? -hops * 150 * 0.25 : 0),
    tatkalSurcharge: quota === "TQ" ? (classCode === "3A" ? 150 : (classCode === "SL" ? 100 : 50)) : 0
  };

  const srcStation = RAILWAY_NETWORK.stations[srcCode] || { name: srcCode };
  const destStation = RAILWAY_NETWORK.stations[destCode] || { name: destCode };

  // Set Subtitle & Journey Summary Pill
  document.getElementById("passengerModalSub").textContent = `Train: ${train.trainId} - ${train.trainName} | Class: ${classCode} | Quota: ${quota === "TQ" ? "TATKAL (TQ)" : "GENERAL (GN)"}`;

  const journeyPill = document.getElementById("modalJourneyPill");
  if (journeyPill) {
    journeyPill.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:center; width:100%;">
        <span><strong>${srcStation.name} (${srcCode}) ➔ ${destStation.name} (${destCode})</strong> | ${train.date}</span>
        <span style="font-size:14px; font-weight:800; color:var(--irctc-orange-dark);">Fare: ₹ ${fare.toFixed(2)}</span>
      </div>
    `;
  }

  // Populate Specific Seat Selection Dropdown from Train's 2-D Segment Matrix
  const specificSeatSelect = document.getElementById("paxSpecificSeat");
  if (specificSeatSelect) {
    specificSeatSelect.innerHTML = `<option value="AUTO" selected>Auto-Assign Best Available Berth</option>`;
    const freeSeats = train.getAvailableSeatIndices(s, e);
    freeSeats.forEach(seatIdx => {
      const opt = document.createElement("option");
      opt.value = seatIdx;
      opt.textContent = `Seat #${seatIdx + 1} - ${getBerthTitle(seatIdx, classCode)} [FREE]`;
      specificSeatSelect.appendChild(opt);
    });
  }

  // Update Proceed Button Text
  const btnProceed = document.getElementById("btnProceedToPay");
  if (btnProceed) {
    btnProceed.innerHTML = `<span>CONTINUE TO PAYMENT (₹ ${fare.toFixed(2)})</span> <span>➔</span>`;
  }

  document.getElementById("passengerModal").classList.add("open");
}

function closePassengerModal() {
  document.getElementById("passengerModal").classList.remove("open");
}

function onBerthPreferenceChanged() {
  const pref = document.getElementById("paxBerthPref").value;
  // If user picks a preference, highlight matching seats in specific dropdown if possible
}

function handlePassengerFormSubmit(e) {
  if (e && e.preventDefault) e.preventDefault();
  if (!activeBookingContext) return;

  const nameVal = document.getElementById("paxName").value.trim();
  const ageVal = parseInt(document.getElementById("paxAge").value);
  const genderVal = document.getElementById("paxGender").value;
  const berthPrefVal = document.getElementById("paxBerthPref").value;
  const specificSeatVal = document.getElementById("paxSpecificSeat").value;
  const mobileVal = document.getElementById("paxMobile").value.trim();
  const autoUpgradeVal = document.getElementById("chkAutoUpgrade").checked;

  if (!nameVal) {
    showToast("Please enter passenger full name.");
    return;
  }

  if (isNaN(ageVal) || ageVal < 5 || ageVal > 110) {
    showToast("Please enter a valid age between 5 and 110.");
    return;
  }

  activeBookingContext.passengerInfo = {
    name: nameVal,
    age: ageVal,
    gender: genderVal,
    berthPref: berthPrefVal,
    specificSeat: specificSeatVal === "AUTO" ? null : parseInt(specificSeatVal),
    mobile: mobileVal,
    autoUpgrade: autoUpgradeVal,
    classCode: activeBookingContext.classCode,
    quota: activeBookingContext.quota
  };

  closePassengerModal();
  openPaymentModal();
}

// =============================================================================
// STEP 4: STREAMLINED DIGITAL PAYMENT GATEWAY WITH EXPANDABLE ACCORDION
// =============================================================================

function openPaymentModal() {
  if (!activeBookingContext) return;
  const { train, classCode, fare, srcCode, destCode, hops, quota, passengerInfo, classPremium, tatkalSurcharge } = activeBookingContext;

  const srcStation = RAILWAY_NETWORK.stations[srcCode] || { name: srcCode };
  const destStation = RAILWAY_NETWORK.stations[destCode] || { name: destCode };

  // Set Streamlined Order Summary
  document.getElementById("paySummaryTrain").textContent = `${train.trainId} | ${train.trainName}`;
  document.getElementById("paySummaryRoute").textContent = `${srcStation.name} (${srcCode}) ➔ ${destStation.name} (${destCode})`;
  document.getElementById("paySummaryPax").textContent = `Passenger: ${passengerInfo.name} (${passengerInfo.age}, ${passengerInfo.gender}) | Class: ${classCode} | Quota: ${quota === "TQ" ? "TATKAL (TQ)" : "GENERAL"}`;
  document.getElementById("paySummaryTotalFare").textContent = `₹ ${fare.toFixed(2)}`;

  // Populate Expandable Stoppages & Fare Calculation
  const stoppagesListEl = document.getElementById("payStoppagesList");
  if (stoppagesListEl) {
    const s = train.getStationIndex(srcCode);
    const e = train.getStationIndex(destCode);
    const segStations = train.stations.slice(s, e + 1);
    stoppagesListEl.innerHTML = `
      <div><strong>Stoppages:</strong> ${segStations.map((st, i) => i === 0 || i === segStations.length - 1 ? `<span style="color:var(--irctc-navy); font-weight:800;">${st}</span>` : st).join(" ➔ ")} (${hops} Hops)</div>
    `;
  }

  const fareFormulaEl = document.getElementById("payFareFormula");
  if (fareFormulaEl) {
    const baseAmount = hops * 150;
    fareFormulaEl.innerHTML = `
      Base Segments: ₹150 × ${hops} hops = ₹${baseAmount.toFixed(2)} + Class Premium: ₹${classPremium.toFixed(2)} ${quota === "TQ" ? `+ Tatkal Surcharge: ₹${tatkalSurcharge.toFixed(2)}` : ""} = <strong>Total: ₹${fare.toFixed(2)}</strong>
    `;
  }

  // Update Pay Button Text & Details
  document.getElementById("btnPayAmountText").textContent = `PAY ₹ ${fare.toFixed(2)} & CONFIRM TICKET`;
  document.getElementById("payWalletBalance").textContent = `₹ ${currentUser.walletBalance.toFixed(2)}`;
  document.getElementById("payWalletDeduct").textContent = `₹ ${fare.toFixed(2)}`;
  document.getElementById("qrAmountSpan").textContent = `₹ ${fare.toFixed(2)}`;

  // Saved Payment Banner
  const savedBanner = document.getElementById("savedPaymentBanner");
  if (currentUser.savedPayment) {
    savedBanner.style.display = "flex";
    document.getElementById("savedPaymentDesc").textContent = currentUser.savedPayment.masked || currentUser.savedPayment.vpa || "Saved Bank Account";
  } else {
    savedBanner.style.display = "none";
  }

  document.getElementById("paymentModal").classList.add("open");
}

function closePaymentModal() {
  document.getElementById("paymentModal").classList.remove("open");
}

function toggleFareBreakdown() {
  const content = document.getElementById("fareBreakdownContent");
  const toggleText = document.getElementById("fareBreakdownToggleText");
  if (!content) return;

  if (content.style.display === "none") {
    content.style.display = "block";
    if (toggleText) toggleText.textContent = "▴ Hide Stoppages & Fare Calculation";
  } else {
    content.style.display = "none";
    if (toggleText) toggleText.textContent = "▾ View Stoppages & Fare Calculation";
  }
}

let activePaymentTab = "WALLET";

function switchPayTab(tabKey) {
  activePaymentTab = tabKey;
  document.querySelectorAll(".pay-tab-btn").forEach(btn => btn.classList.remove("active"));
  document.querySelectorAll(".pay-tab-content").forEach(c => c.classList.remove("active"));

  if (event && event.currentTarget) event.currentTarget.classList.add("active");
  const content = document.getElementById(`payContent-${tabKey}`);
  if (content) content.classList.add("active");
}

function formatCardNumber(input) {
  let val = input.value.replace(/\D/g, "");
  val = val.substring(0, 16);
  const formatted = val.replace(/(\d{4})/g, "$1 ").trim();
  input.value = formatted;
  document.getElementById("vcNumber").textContent = formatted || "•••• •••• •••• 8821";
}

function updateCardHolder(input) {
  document.getElementById("vcName").textContent = input.value.toUpperCase() || "RAHUL KUMAR";
}

function formatCardExpiry(input) {
  let val = input.value.replace(/\D/g, "");
  if (val.length >= 2) val = val.substring(0, 2) + "/" + val.substring(2, 4);
  input.value = val;
  document.getElementById("vcExp").textContent = val || "08/28";
}

let selectedBankName = "State Bank of India (SBI)";
function selectBank(el, bankName) {
  document.querySelectorAll(".bank-tile").forEach(t => t.classList.remove("selected"));
  el.classList.add("selected");
  selectedBankName = bankName;
}

function payWithSavedMethod() {
  if (!currentUser.savedPayment) return;
  activePaymentTab = currentUser.savedPayment.type;
  executePaymentAndBook();
}

function executePaymentAndBook() {
  if (!activeBookingContext) return;
  const { train, srcCode, destCode, fare, passengerInfo } = activeBookingContext;

  let paymentDetails = { method: activePaymentTab };

  if (activePaymentTab === "UPI") {
    const vpa = document.getElementById("upiVpaInput").value.trim();
    if (!vpa || !vpa.includes("@")) {
      showToast("Please enter a valid UPI ID (VPA).");
      return;
    }
    paymentDetails.vpa = vpa;
    if (document.getElementById("saveUpiCheckbox").checked) {
      currentUser.savedPayment = { type: "UPI", masked: `UPI VPA: ${vpa}` };
    }
  } else if (activePaymentTab === "CARD") {
    const cardNo = document.getElementById("cardNoInput").value.trim();
    const cholder = document.getElementById("cardHolderInput").value.trim();
    const cexp = document.getElementById("cardExpiryInput").value.trim();
    if (cardNo.length < 16) {
      showToast("Please enter a valid 16-digit card number.");
      return;
    }
    const cleanNo = cardNo.replace(/\s+/g, "");
    const last4 = cleanNo.slice(-4);
    paymentDetails.masked = `RuPay Card •••• ${last4}`;
    if (document.getElementById("saveCardCheckbox").checked) {
      currentUser.savedPayment = {
        type: "CARD",
        masked: `RuPay Card •••• ${last4}`,
        holder: cholder,
        expiry: cexp
      };
    }
  } else if (activePaymentTab === "NETBANKING") {
    paymentDetails.bank = selectedBankName;
    if (document.getElementById("saveBankCheckbox").checked) {
      currentUser.savedPayment = { type: "NETBANKING", masked: `Net Banking: ${selectedBankName}` };
    }
  }

  closePaymentModal();

  // Execute Reservation on Central Engine
  const res = engine.bookTicket(currentUser, train, srcCode, destCode, activePaymentTab, paymentDetails, fare, passengerInfo);
  updateUserHeader();

  if (res.success) {
    showToast(res.status === "CONFIRMED" ? "🎉 Reservation Confirmed! ERS Generated." : "⏳ Waitlisted Ticket Issued.");
    renderBoardingPassSlip(res.passenger);
    switchTab("tab-pnr");
  } else {
    showToast(res.message);
  }
}

function handlePnrLookup(e) {
  if (e && e.preventDefault) e.preventDefault();
  const pnrVal = document.getElementById("pnrQueryInput").value.trim();
  if (!pnrVal) return;

  const passenger = engine.pnrMap.get(pnrVal);
  if (!passenger) {
    showToast(`No reservation record found for PNR: ${pnrVal}`);
    document.getElementById("pnrSlipContainer").style.display = "none";
  } else {
    renderBoardingPassSlip(passenger);
  }
}

function renderBoardingPassSlip(p) {
  const container = document.getElementById("pnrSlipContainer");
  container.style.display = "block";

  const srcName = RAILWAY_NETWORK.stations[p.src]?.name || p.src;
  const destName = RAILWAY_NETWORK.stations[p.dest]?.name || p.dest;

  container.innerHTML = `
    <div class="ers-ticket-card">
      <div class="ers-header">
        <div class="ers-title">INDIAN RAILWAYS &bull; ELECTRONIC RESERVATION SLIP (ERS)</div>
        <div class="ers-pnr-badge">PNR: ${p.pnr}</div>
      </div>

      <div class="ers-body">
        <div class="ers-grid-details">
          <div class="ers-detail-item">
            <div class="lbl">Train Number &amp; Name</div>
            <div class="val">${p.trainId} / ${p.trainName}</div>
          </div>
          <div class="ers-detail-item">
            <div class="lbl">Journey Date</div>
            <div class="val">${p.date}</div>
          </div>
          <div class="ers-detail-item">
            <div class="lbl">Booking Status</div>
            <div class="val"><span class="badge ${p.status === "CONFIRMED" ? "badge-success" : p.status === "WAITING" ? "badge-warning" : "badge-danger"}">${p.status}</span></div>
          </div>
          <div class="ers-detail-item">
            <div class="lbl">Boarding Station (From)</div>
            <div class="val">${srcName} (${p.src})</div>
          </div>
          <div class="ers-detail-item">
            <div class="lbl">Destination Station (To)</div>
            <div class="val">${destName} (${p.dest})</div>
          </div>
          <div class="ers-detail-item">
            <div class="lbl">Coach / Berth Assignment</div>
            <div class="val" style="color:var(--irctc-navy);">${p.assignedSeat >= 0 ? `Coach ${p.coach} | Seat #${p.assignedSeat} (${p.berth})` : `Waitlist Position: WL ${p.waitlistPosition || 1}`}</div>
          </div>
          <div class="ers-detail-item">
            <div class="lbl">Booked Account User ID</div>
            <div class="val">${p.userId}</div>
          </div>
          <div class="ers-detail-item">
            <div class="lbl">Payment Method Used</div>
            <div class="val">${p.paymentMethod}</div>
          </div>
          <div class="ers-detail-item">
            <div class="lbl">Total Fare Paid</div>
            <div class="val text-orange">₹ ${p.farePaid.toFixed(2)}</div>
          </div>
        </div>

        <table class="ers-passengers-table">
          <thead>
            <tr>
              <th>Passenger Name</th>
              <th>Age &amp; Gender</th>
              <th>Class &amp; Quota</th>
              <th>Booking Status</th>
              <th>Current Status</th>
              <th>Coach / Berth</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>${(p.passengerName || p.userId).toUpperCase()}</strong></td>
              <td>${p.passengerAge || 28} / ${p.passengerGender || "MALE"}</td>
              <td>${p.selectedClass || "3A"} (${p.quota === "TQ" ? "TATKAL" : p.quota || "GENERAL"})</td>
              <td>${p.status}</td>
              <td><span class="badge ${p.status === "CONFIRMED" ? "badge-success" : "badge-warning"}">${p.status}</span></td>
              <td>${p.assignedSeat >= 0 ? `${p.coach} / Seat #${p.assignedSeat + 1} (${p.berth})` : `WL Queue (WL ${p.waitlistPosition || 1})`}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="ers-footer-actions">
        <button class="btn btn-outline" onclick="window.print()">🖨️ Print Electronic Ticket</button>
        ${p.status !== "CANCELLED" ? `
          <button class="btn btn-danger" onclick="initiateCancellation('${p.pnr}')">❌ Cancel Ticket (Auto-Refund)</button>
        ` : `
          <span class="badge badge-danger">Ticket Cancelled</span>
        `}
      </div>
    </div>
  `;
}

function initiateCancellation(pnr) {
  if (!confirm(`Are you sure you want to cancel PNR ${pnr}?\nRefund will be calculated after IRCTC cancellation fee deduction (₹60.00).`)) return;

  const res = engine.cancelTicket(pnr);
  updateUserHeader();

  if (res.success) {
    let msg = `Cancelled PNR ${res.pnr}.\nRefund credited to wallet: ₹${res.refund.toFixed(2)} (Fee ₹${res.deduction.toFixed(2)} deducted).`;
    if (res.promotionMessage) msg += `\n\n📢 CASCADE PROMOTION: ${res.promotionMessage}`;
    alert(msg);
    handlePnrLookup();
    renderMyBookings();
  } else {
    alert(res.message);
  }
}

// =============================================================================
// SECTION 7: MY BOOKINGS VIEW
// =============================================================================

function renderMyBookings() {
  const tbody = document.getElementById("myBookingsTbody");
  tbody.innerHTML = "";

  if (!currentUser) return;
  const list = engine.getUserBookings(currentUser.userId);

  if (list.length === 0) {
    tbody.innerHTML = `<tr><td colspan="10" style="text-align:center; padding: 30px; color:var(--text-muted);">No booking history found for account "${currentUser.userId}".</td></tr>`;
    return;
  }

  list.forEach(b => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><strong>${b.pnr}</strong></td>
      <td>${b.trainId} - ${b.trainName}</td>
      <td>${b.date}</td>
      <td>${b.src} ➔ ${b.dest}</td>
      <td><span class="badge ${b.status === "CONFIRMED" ? "badge-success" : b.status === "WAITING" ? "badge-warning" : "badge-danger"}">${b.status}</span></td>
      <td>${b.assignedSeat >= 0 ? `${b.coach} / #${b.assignedSeat}` : "--"}</td>
      <td><strong>₹ ${b.farePaid.toFixed(2)}</strong></td>
      <td><span class="badge badge-success">${b.paymentMethod}</span></td>
      <td style="font-size:11px; color:var(--text-muted);">${new Date(b.bookingTimestamp).toLocaleTimeString()}</td>
      <td>
        <button class="btn btn-sm btn-outline" onclick="viewPnrDirect('${b.pnr}')">View ERS</button>
        ${b.status !== "CANCELLED" ? `<button class="btn btn-sm btn-danger" onclick="initiateCancellation('${b.pnr}')">Cancel</button>` : ""}
      </td>
    `;
    tbody.appendChild(tr);
  });
}

function viewPnrDirect(pnr) {
  document.getElementById("pnrQueryInput").value = pnr;
  switchTab("tab-pnr");
  handlePnrLookup();
}

// =============================================================================
// SECTION 8: ADMIN TRAIN MANAGEMENT & CORRIDOR ROUTE BUILDER
// =============================================================================

function initAdminCorridorBuilder() {
  const origSelect = document.getElementById("admOriginStation");
  const termSelect = document.getElementById("admTerminusStation");

  origSelect.innerHTML = "";
  termSelect.innerHTML = "";

  Object.values(RAILWAY_NETWORK.stations).forEach(st => {
    const o1 = document.createElement("option");
    o1.value = st.code;
    o1.textContent = `${st.name} (${st.code})`;
    origSelect.appendChild(o1);

    const o2 = document.createElement("option");
    o2.value = st.code;
    o2.textContent = `${st.name} (${st.code})`;
    termSelect.appendChild(o2);
  });

  origSelect.value = "NDLS";
  termSelect.value = "MMCT";

  onEndpointChanged();
}

function onEndpointChanged() {
  const orig = document.getElementById("admOriginStation").value;
  const term = document.getElementById("admTerminusStation").value;

  const matchingCorridors = RailCorridorValidator.findMatchingCorridors(orig, term);
  const corridorBanner = document.getElementById("corridorDetectedBanner");
  const corridorNameEl = document.getElementById("detectedCorridorName");

  if (matchingCorridors.length > 0) {
    corridorBanner.style.display = "flex";
    corridorNameEl.textContent = matchingCorridors.map(c => c.name).join(" / ");
  } else {
    corridorBanner.style.display = "flex";
    corridorNameEl.textContent = "Custom Indian Railways Network Cross-Corridor Route";
  }

  // Filter available intermediate stations dropdown to strictly valid corridor stations!
  const intermediateSelect = document.getElementById("admIntermediateSelect");
  intermediateSelect.innerHTML = "";

  const validStops = RailCorridorValidator.getValidIntermediateStations(orig, term);

  if (validStops.length > 0) {
    validStops.forEach(st => {
      const opt = document.createElement("option");
      opt.value = st.code;
      opt.textContent = `${st.name} (${st.code}) - [Corridor Stop]`;
      intermediateSelect.appendChild(opt);
    });
  } else {
    // Non-stop direct corridor or cross-corridor
    Object.values(RAILWAY_NETWORK.stations).forEach(st => {
      if (st.code !== orig && st.code !== term) {
        const opt = document.createElement("option");
        opt.value = st.code;
        opt.textContent = `${st.name} (${st.code})`;
        intermediateSelect.appendChild(opt);
      }
    });
  }

  // Filter existing builder stops
  builderIntermediateStops = builderIntermediateStops.filter(code => code !== orig && code !== term);

  renderStopsListAndTrack();
}

function handleAddIntermediateStop() {
  const orig = document.getElementById("admOriginStation").value;
  const term = document.getElementById("admTerminusStation").value;
  const newStop = document.getElementById("admIntermediateSelect").value;

  if (!newStop) return;

  // Run Real-Time Geographic Corridor Validation
  const validation = RailCorridorValidator.validateStopAddition(orig, term, builderIntermediateStops, newStop);

  if (!validation.valid) {
    alert(validation.message);
    return;
  }

  builderIntermediateStops.push(newStop);
  renderStopsListAndTrack();
}

function removeIntermediateStop(index) {
  builderIntermediateStops.splice(index, 1);
  renderStopsListAndTrack();
}

function moveStopUp(index) {
  if (index > 0) {
    const tmp = builderIntermediateStops[index];
    builderIntermediateStops[index] = builderIntermediateStops[index - 1];
    builderIntermediateStops[index - 1] = tmp;
    renderStopsListAndTrack();
  }
}

function moveStopDown(index) {
  if (index < builderIntermediateStops.length - 1) {
    const tmp = builderIntermediateStops[index];
    builderIntermediateStops[index] = builderIntermediateStops[index + 1];
    builderIntermediateStops[index + 1] = tmp;
    renderStopsListAndTrack();
  }
}

function renderStopsListAndTrack() {
  const container = document.getElementById("stopsListContainer");
  container.innerHTML = "";

  if (builderIntermediateStops.length === 0) {
    container.innerHTML = `<div style="text-align:center; padding: 12px; color:var(--text-muted); font-size:12px;">No intermediate stops added yet (Non-stop direct service). Use [+] Add Stop above.</div>`;
  } else {
    builderIntermediateStops.forEach((stCode, idx) => {
      const st = RAILWAY_NETWORK.stations[stCode] || { name: stCode };
      const row = document.createElement("div");
      row.className = "stop-item-row";
      row.innerHTML = `
        <span class="sir-st-name">Stop #${idx + 1}: ${st.name} (${stCode})</span>
        <div class="sir-actions">
          <button type="button" class="btn-icon" onclick="moveStopUp(${idx})" ${idx === 0 ? "disabled" : ""}>▲ Up</button>
          <button type="button" class="btn-icon" onclick="moveStopDown(${idx})" ${idx === builderIntermediateStops.length - 1 ? "disabled" : ""}>▼ Down</button>
          <button type="button" class="btn-icon" style="color:var(--red-err);" onclick="removeIntermediateStop(${idx})">✕ Remove</button>
        </div>
      `;
      container.appendChild(row);
    });
  }

  // Update Visual Track Line
  const orig = document.getElementById("admOriginStation").value;
  const term = document.getElementById("admTerminusStation").value;
  const fullChain = [orig, ...builderIntermediateStops, term];

  const trackLineEl = document.getElementById("routeTrackLine");
  trackLineEl.innerHTML = fullChain.map((st, i) => `
    <span class="track-station">${RAILWAY_NETWORK.stations[st]?.name || st}</span>
    ${i < fullChain.length - 1 ? '<span class="track-arrow">━━━➔</span>' : ''}
  `).join("");

  const numStns = fullChain.length;
  const segments = numStns - 1;
  const permutations = (numStns * (numStns - 1)) / 2;
  document.getElementById("routeStatsSummary").textContent = 
    `Total Stations: ${numStns} | Segments: ${segments} | Route Pairs in Matrix: ${permutations} | Dynamic Rate: ₹150/hop`;
}

function resetTrainBuilder() {
  editingTrainId = null;
  editingTrainDate = null;
  document.getElementById("builderFormTitle").textContent = "➕ Train Route Configurator";
  document.getElementById("btnSubmitTrainText").textContent = "➕ INJECT TRAIN INTO ROUTE MATRIX";

  document.getElementById("admTrainNo").value = "12431";
  document.getElementById("admTrainName").value = "TRIVANDRUM RAJDHANI";
  document.getElementById("admTrainDate").value = "2026-11-01";
  document.getElementById("admTrainCapacity").value = "4";

  document.getElementById("admOriginStation").value = "NDLS";
  document.getElementById("admTerminusStation").value = "MMCT";

  builderIntermediateStops = ["KOTA", "RTM", "BRC", "ST"];
  onEndpointChanged();
}

function handleSaveTrain(e) {
  if (e && e.preventDefault) e.preventDefault();
  const id = document.getElementById("admTrainNo").value.trim();
  const name = document.getElementById("admTrainName").value.trim();
  const date = document.getElementById("admTrainDate").value.trim();
  const cap = parseInt(document.getElementById("admTrainCapacity").value, 10);

  const orig = document.getElementById("admOriginStation").value;
  const term = document.getElementById("admTerminusStation").value;

  if (orig === term) {
    alert("Origin and Terminus cannot be identical.");
    return;
  }

  const fullStations = [orig, ...builderIntermediateStops, term];

  // If editing, remove old instance first
  if (editingTrainId) {
    engine.removeTrain(editingTrainId, editingTrainDate);
  }

  const ti = new TrainInstance(id, name, date, fullStations, cap);
  engine.addTrain(ti);

  showToast(`Train ${id} (${name}) successfully injected into Route Matrix!`);
  renderActiveTrainsDirectory();
  populateDsaTrainDropdown();
  resetTrainBuilder();
}

function renderActiveTrainsDirectory() {
  const tbody = document.getElementById("adminTrainsTbody");
  tbody.innerHTML = "";

  const list = engine.getAllUniqueTrains();
  document.getElementById("activeTrainsCountSub").textContent = `${list.length} trains registered in Central Route Matrix`;

  list.forEach(t => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><strong>${t.trainId}</strong><br><small style="color:var(--text-muted);">${t.trainName}</small></td>
      <td>${t.date}</td>
      <td style="font-size:11.5px; color:#4338CA;">${t.getRouteChainDisplay()}</td>
      <td>${t.seatCapacity} Seats</td>
      <td><span class="badge badge-success">${t.confirmedRoster.count}</span></td>
      <td><span class="badge ${t.waitingList.count > 0 ? "badge-warning" : "badge-success"}">${t.waitingList.count}</span></td>
      <td>
        <button class="btn btn-sm btn-outline" onclick="editTrainDirect('${t.trainId}', '${t.date}')">✏️ Edit</button>
        <button class="btn btn-sm btn-danger" onclick="deleteTrainDirect('${t.trainId}', '${t.date}')">❌ Remove</button>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

function editTrainDirect(trainId, date) {
  const train = engine.findTrainInstance(trainId, date);
  if (!train) return;

  editingTrainId = train.trainId;
  editingTrainDate = train.date;

  document.getElementById("builderFormTitle").textContent = `✏️ Edit Train ${train.trainId}`;
  document.getElementById("btnSubmitTrainText").textContent = "💾 SAVE UPDATED TRAIN MATRIX";

  document.getElementById("admTrainNo").value = train.trainId;
  document.getElementById("admTrainName").value = train.trainName;
  document.getElementById("admTrainDate").value = train.date;
  document.getElementById("admTrainCapacity").value = train.seatCapacity;

  document.getElementById("admOriginStation").value = train.stations[0];
  document.getElementById("admTerminusStation").value = train.stations[train.stations.length - 1];

  builderIntermediateStops = train.stations.slice(1, train.stations.length - 1);
  onEndpointChanged();

  document.getElementById("trainBuilderForm").scrollIntoView({ behavior: "smooth" });
}

function deleteTrainDirect(trainId, date) {
  if (!confirm(`Are you sure you want to permanently remove Train ${trainId} on ${date}?\nThis will purge all route combinations from the matrix.`)) return;

  const ok = engine.removeTrain(trainId, date);
  if (ok) {
    showToast(`Train ${trainId} removed from Route Matrix.`);
    renderActiveTrainsDirectory();
    populateDsaTrainDropdown();
  }
}

// =============================================================================
// SECTION 9: DSA LIVE ENGINE & 2-D SEGMENT MATRIX INSPECTOR
// =============================================================================

function populateDsaTrainDropdown() {
  const select = document.getElementById("dsaTrainSelect");
  select.innerHTML = "";
  const trains = engine.getAllUniqueTrains();
  trains.forEach(t => {
    const opt = document.createElement("option");
    opt.value = `${t.trainId}_${t.date}`;
    opt.textContent = `${t.trainId} - ${t.trainName} (${t.date})`;
    select.appendChild(opt);
  });
  renderDsaVisualizer();
}

function renderDsaVisualizer() {
  const selectVal = document.getElementById("dsaTrainSelect").value;
  if (!selectVal) return;
  const [tId, tDate] = selectVal.split("_");
  const train = engine.findTrainInstance(tId, tDate);
  if (!train) return;

  // Render 2-D Segment Matrix
  const gridContainer = document.getElementById("matrixGridContainer");
  gridContainer.innerHTML = "";

  const table = document.createElement("table");
  table.className = "matrix-grid-table";

  // Header Row: Seat Numbers
  let thead = "<tr><th>Segment / Station Hop ➔</th>";
  for (let s = 0; s < train.seatCapacity; s++) {
    thead += `<th>Seat #${s}</th>`;
  }
  thead += "</tr>";

  // Body Rows: Station Segments
  let tbody = "";
  for (let seg = 0; seg < train.numSegments; seg++) {
    const segName = `${train.stations[seg]} ➔ ${train.stations[seg + 1]}`;
    tbody += `<tr><td style="text-align:left; font-weight:700;">${segName}</td>`;
    for (let s = 0; s < train.seatCapacity; s++) {
      const isBooked = train.segmentInventory[seg][s];
      tbody += `<td class="${isBooked ? "cell-booked" : "cell-free"}">${isBooked ? "OCCUPIED" : "FREE"}</td>`;
    }
    tbody += `</tr>`;
  }

  table.innerHTML = thead + tbody;
  gridContainer.appendChild(table);

  // Render Confirmed Singly Linked List
  const confContainer = document.getElementById("dsaConfirmedList");
  confContainer.innerHTML = "";
  const confSnap = train.confirmedRoster.snapshot();
  document.getElementById("dsaConfirmedCount").textContent = `${confSnap.length} Passengers`;

  if (confSnap.length === 0) {
    confContainer.innerHTML = `<div style="text-align:center; padding: 16px; color:var(--text-muted); font-size:12px;">No confirmed passengers on roster.</div>`;
  } else {
    confSnap.forEach((p, idx) => {
      const el = document.createElement("div");
      el.className = "node-item";
      el.innerHTML = `
        <div>
          <span style="font-size:10px; color:var(--text-muted);">[NODE #${idx + 1}]</span>
          <span class="pnr">${p.pnr}</span> &bull; <span>${p.userId}</span>
          <div style="font-size:11px; color:var(--text-muted);">${p.src} ➔ ${p.dest} (₹${p.farePaid})</div>
        </div>
        <div class="seat">Seat #${p.assignedSeat} (${p.berth})</div>
      `;
      confContainer.appendChild(el);
    });
  }

  // Render FIFO Waitlist Pointer Queue
  const waitContainer = document.getElementById("dsaWaitlistList");
  waitContainer.innerHTML = "";
  const waitSnap = train.waitingList.snapshot();
  document.getElementById("dsaWaitlistCount").textContent = `${waitSnap.length} Waiting`;

  if (waitSnap.length === 0) {
    waitContainer.innerHTML = `<div style="text-align:center; padding: 16px; color:var(--text-muted); font-size:12px;">FIFO waitlist queue is empty. All booking requests accommodated.</div>`;
  } else {
    waitSnap.forEach((p, idx) => {
      const el = document.createElement("div");
      el.className = "node-item";
      el.innerHTML = `
        <div>
          <span style="font-size:10px; color:var(--text-muted);">${idx === 0 ? "[FRONT]" : idx === waitSnap.length - 1 ? "[REAR]" : `[POS #${idx + 1}]`}</span>
          <span class="pnr">${p.pnr}</span> &bull; <span>${p.userId}</span>
          <div style="font-size:11px; color:var(--text-muted);">${p.src} ➔ ${p.dest} (Hold: ₹${p.farePaid})</div>
        </div>
        <div style="color:var(--amber-warn); font-weight:700;">WL #${idx + 1}</div>
      `;
      waitContainer.appendChild(el);
    });
  }
}

// =============================================================================
// SECTION 10: AUTHENTICATION MODAL & TOAST
// =============================================================================

function openAuthModal() {
  document.getElementById("authModal").classList.add("open");
}

function closeAuthModal() {
  document.getElementById("authModal").classList.remove("open");
}

function closeModalOnOverlay(e, modalId) {
  if (e.target.id === modalId) {
    document.getElementById(modalId).classList.remove("open");
  }
}

function quickFillAuth(userId, pass) {
  document.getElementById("authUserId").value = userId;
  document.getElementById("authPassword").value = pass;
  handleAuthSubmit(new Event("submit"));
}

function handleAuthSubmit(e) {
  if (e && e.preventDefault) e.preventDefault();
  const u = document.getElementById("authUserId").value.trim();
  const p = document.getElementById("authPassword").value.trim();
  const errEl = document.getElementById("authErrorMsg");

  const user = engine.userMap.get(u);
  if (user && user.password === p) {
    currentUser = user;
    errEl.textContent = "";
    closeAuthModal();
    updateUserHeader();
    renderNavigation();

    showToast(`Welcome back, ${user.userId}! Logged in as ${user.isAdmin ? "Railway Admin Officer" : "Passenger"}.`);

    if (user.isAdmin) {
      switchTab("tab-admin");
    } else {
      switchTab("tab-book");
    }
  } else {
    errEl.textContent = "Invalid User ID or Password. Please try again.";
  }
}

function showToast(msg) {
  const toast = document.getElementById("toastNotification");
  toast.textContent = msg;
  toast.classList.add("show");
  setTimeout(() => toast.classList.remove("show"), 3500);
}
