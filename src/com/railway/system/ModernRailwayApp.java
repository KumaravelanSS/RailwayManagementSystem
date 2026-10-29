package com.railway.system;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

// ============================================================================
//  IRCTC NextGen Ultra-Low-Latency Reservation Core  |  Custom DSA Architecture
//  ============================================================================
//  Core Data Structures:
//    - Separate-Chaining Prime-Bucket Hash Maps (UserMap, PnrMap, RouteMatrix) => O(1)
//    - Singly Linked List Passenger Roster with Head/Tail Cursors            => O(1) insert
//    - FIFO Waitlist Pointer Queue with ReentrantLock Synchronisation         => O(1) enq/deq
//    - 2-D Segment Inventory Matrix (Station Segments x Seat Capacity Bitmap) => O(L x S)
//  Concurrency : ReentrantLock guards on all mutable engine state
//  Pricing     : Dynamic Spatial Segment Matrix Calculation (Rs. 150.00 x Hops)
// ============================================================================

// ---------------------------------------------------------------------------
// SECTION 1 - PRIMITIVE CUSTOM DSA NODES
// ---------------------------------------------------------------------------

class UserNode {
    String userId, passwordHash;
    boolean isAdmin;
    double walletBalance;
    // Saved Payment Credentials (RBI / Government Tokenization Compliant - No raw CVV)
    String savedUpiId;
    String savedCardMasked; // e.g. "RuPay **** **** **** 8821"
    String savedCardExpiry; // e.g. "08/28"
    String savedCardHolder;
    String savedBankName;   // e.g. "State Bank of India"
    UserNode next;

    UserNode(String u, String p, boolean a) {
        userId = u; passwordHash = p; isAdmin = a; walletBalance = 10_000.0;
        savedUpiId = null; savedCardMasked = null; savedCardExpiry = null;
        savedCardHolder = null; savedBankName = null;
    }
}

class PassengerNode {
    String pnr, userId, trainId, trainName, date, src, dest, status;
    int assignedSeat;
    double farePaid;
    String paymentMethod; // "WALLET", "UPI", "CARD", "NETBANKING"
    long bookingTimestamp;
    PassengerNode next;

    PassengerNode(String pnr, String uid, String tid, String tn,
                  String d, String s, String dt, String st, double f, String payMethod) {
        this.pnr = pnr; userId = uid; trainId = tid; trainName = tn;
        date = d; src = s; dest = dt; status = st;
        assignedSeat = -1; farePaid = f; paymentMethod = payMethod;
        bookingTimestamp = System.currentTimeMillis();
    }
}

class PnrMapNode {
    String pnr; PassengerNode targetNode; PnrMapNode next;
    PnrMapNode(String p, PassengerNode t) { pnr = p; targetNode = t; }
}

class RouteMatrixNode {
    String key; TrainInstance[] instances; int count; RouteMatrixNode next;
    RouteMatrixNode(String k) { key = k; instances = new TrainInstance[8]; count = 0; }
    void addTrain(TrainInstance ti) {
        if (count >= instances.length) {
            TrainInstance[] tmp = new TrainInstance[instances.length * 2];
            System.arraycopy(instances, 0, tmp, 0, instances.length);
            instances = tmp;
        }
        instances[count++] = ti;
    }
}

// ---------------------------------------------------------------------------
// SECTION 2 - FIFO QUEUE (O(1) Enqueue / Dequeue)
// ---------------------------------------------------------------------------

class FIFOQueue {
    PassengerNode front, rear;
    private final ReentrantLock lock = new ReentrantLock();

    void enqueue(PassengerNode node) {
        lock.lock();
        try {
            node.next = null;
            if (rear == null) { front = rear = node; } else { rear.next = node; rear = node; }
        } finally { lock.unlock(); }
    }

    PassengerNode dequeue() {
        lock.lock();
        try {
            if (front == null) return null;
            PassengerNode tmp = front; front = front.next;
            if (front == null) rear = null;
            tmp.next = null;
            return tmp;
        } finally { lock.unlock(); }
    }

    boolean isEmpty() { return front == null; }

    List<PassengerNode> snapshot() {
        lock.lock();
        try {
            List<PassengerNode> l = new ArrayList<>();
            PassengerNode c = front; while (c != null) { l.add(c); c = c.next; }
            return l;
        } finally { lock.unlock(); }
    }
}

// ---------------------------------------------------------------------------
// SECTION 3 - TRAIN INSTANCE (2-D Segment Matrix + Linked List + FIFO Queue)
// ---------------------------------------------------------------------------

class TrainInstance {
    final String trainId, trainName, date;
    final String[] stations;
    final int seatCapacity, numSegments;
    boolean[][] segmentInventory;
    PassengerNode confirmedHead, confirmedTail;
    FIFOQueue waitingList;
    final ReentrantLock bookingLock = new ReentrantLock();
    static final double BASE = 150.0;

    TrainInstance(String id, String nm, String dt, String[] stns, int cap) {
        trainId = id; trainName = nm; date = dt; stations = stns; seatCapacity = cap;
        numSegments = stns.length - 1;
        segmentInventory = new boolean[numSegments][cap];
        waitingList = new FIFOQueue();
    }

    int getStationIndex(String s) {
        for (int i = 0; i < stations.length; i++) {
            if (stations[i].equalsIgnoreCase(s)) return i;
        }
        return -1;
    }

    double computeFare(int s, int e) { return (e - s) * BASE; }

    int checkAvailability(int s, int e) {
        for (int seat = 0; seat < seatCapacity; seat++) {
            boolean free = true;
            for (int seg = s; seg < e; seg++) {
                if (segmentInventory[seg][seat]) { free = false; break; }
            }
            if (free) return seat;
        }
        return -1;
    }

    int countAvailable(int s, int e) {
        int cnt = 0;
        for (int seat = 0; seat < seatCapacity; seat++) {
            boolean free = true;
            for (int seg = s; seg < e; seg++) {
                if (segmentInventory[seg][seat]) { free = false; break; }
            }
            if (free) cnt++;
        }
        return cnt;
    }

    void allocateSegments(int s, int e, int seat) {
        for (int seg = s; seg < e; seg++) segmentInventory[seg][seat] = true;
    }

    void deallocateSegments(int s, int e, int seat) {
        for (int seg = s; seg < e; seg++) segmentInventory[seg][seat] = false;
    }

    void insertConfirmed(PassengerNode n) {
        n.next = null;
        if (confirmedHead == null) { confirmedHead = confirmedTail = n; }
        else { confirmedTail.next = n; confirmedTail = n; }
    }

    boolean removeConfirmed(String pnr) {
        PassengerNode c = confirmedHead, p = null;
        while (c != null) {
            if (c.pnr.equals(pnr)) {
                if (p == null) confirmedHead = c.next; else p.next = c.next;
                if (c == confirmedTail) confirmedTail = p;
                c.next = null;
                return true;
            }
            p = c; c = c.next;
        }
        return false;
    }

    List<PassengerNode> confirmedSnapshot() {
        List<PassengerNode> l = new ArrayList<>();
        PassengerNode c = confirmedHead; while (c != null) { l.add(c); c = c.next; }
        return l;
    }

    String getRouteDisplay() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stations.length; i++) {
            sb.append(stations[i]);
            if (i < stations.length - 1) sb.append(" -> ");
        }
        return sb.toString();
    }
}

// ---------------------------------------------------------------------------
// SECTION 4 - CENTRAL DATA ENGINE
// ---------------------------------------------------------------------------

class CentralDataEngine {
    static final int BUCKETS = 1009;
    final UserNode[] userBuckets = new UserNode[BUCKETS];
    final PnrMapNode[] pnrBuckets = new PnrMapNode[BUCKETS];
    final RouteMatrixNode[] routeBuckets = new RouteMatrixNode[BUCKETS];
    private final ReentrantLock UL = new ReentrantLock();
    private final ReentrantLock PL = new ReentrantLock();
    private final ReentrantLock RL = new ReentrantLock();
    private long pnrSeed = 4_290_000_000L;
    static final double TXN_FEE = 60.0;

    CentralDataEngine() {
        registerUser("passenger1", "pass123", false);
        registerUser("passenger2", "pass123", false);
        registerUser("admin@irctc.res.in", "adminRoot", true);
        bootstrap();
    }

    private int h(String k) {
        int h = 0;
        for (char c : k.toCharArray()) h = (h * 31 + c) % BUCKETS;
        return Math.abs(h);
    }

    void registerUser(String uid, String pass, boolean isAdmin) {
        UL.lock();
        try {
            int idx = h(uid); String hp = sha(pass);
            UserNode c = userBuckets[idx];
            while (c != null) { if (c.userId.equals(uid)) return; c = c.next; }
            UserNode n = new UserNode(uid, hp, isAdmin);
            n.next = userBuckets[idx];
            userBuckets[idx] = n;
        } finally { UL.unlock(); }
    }

    UserNode authenticate(String uid, String pass) {
        int idx = h(uid); String hp = sha(pass);
        UserNode c = userBuckets[idx];
        while (c != null) {
            if (c.userId.equals(uid) && c.passwordHash.equals(hp)) return c;
            c = c.next;
        }
        return null;
    }

    UserNode getUserNode(String uid) {
        int idx = h(uid); UserNode c = userBuckets[idx];
        while (c != null) { if (c.userId.equals(uid)) return c; c = c.next; }
        return null;
    }

    void addTrainInstance(TrainInstance ti) {
        RL.lock();
        try {
            String[] stns = ti.stations;
            for (int i = 0; i < stns.length; i++) {
                for (int j = i + 1; j < stns.length; j++) {
                    String key = (stns[i] + "_" + stns[j] + "_" + ti.date).toUpperCase();
                    int idx = h(key);
                    RouteMatrixNode c = routeBuckets[idx], t = null;
                    while (c != null) {
                        if (c.key.equals(key)) { t = c; break; }
                        c = c.next;
                    }
                    if (t == null) {
                        t = new RouteMatrixNode(key);
                        t.next = routeBuckets[idx];
                        routeBuckets[idx] = t;
                    }
                    boolean dup = false;
                    for (int k = 0; k < t.count; k++) {
                        if (t.instances[k] == ti) { dup = true; break; }
                    }
                    if (!dup) t.addTrain(ti);
                }
            }
        } finally { RL.unlock(); }
    }

    boolean removeTrainInstance(String trainId, String date) {
        RL.lock();
        try {
            boolean removed = false;
            for (int i = 0; i < BUCKETS; i++) {
                RouteMatrixNode c = routeBuckets[i];
                while (c != null) {
                    int v = 0;
                    for (int j = 0; j < c.count; j++) {
                        TrainInstance ti = c.instances[j];
                        boolean matchId = ti.trainId.equalsIgnoreCase(trainId);
                        boolean matchDate = (date == null || date.isEmpty() || ti.date.equalsIgnoreCase(date));
                        if (matchId && matchDate) {
                            removed = true;
                        } else {
                            c.instances[v++] = ti;
                        }
                    }
                    c.count = v;
                    c = c.next;
                }
            }
            return removed;
        } finally { RL.unlock(); }
    }

    List<TrainInstance> getAllUniqueTrains() {
        RL.lock();
        try {
            List<TrainInstance> list = new ArrayList<>();
            for (int i = 0; i < BUCKETS; i++) {
                RouteMatrixNode c = routeBuckets[i];
                while (c != null) {
                    for (int j = 0; j < c.count; j++) {
                        TrainInstance ti = c.instances[j];
                        boolean exists = false;
                        for (TrainInstance ex : list) {
                            if (ex.trainId.equalsIgnoreCase(ti.trainId) && ex.date.equalsIgnoreCase(ti.date)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) list.add(ti);
                    }
                    c = c.next;
                }
            }
            list.sort(Comparator.comparing(t -> t.trainId));
            return list;
        } finally { RL.unlock(); }
    }

    TrainInstance[] queryTrains(String src, String dest, String date) {
        String key = (src + "_" + dest + "_" + date).toUpperCase();
        int idx = h(key);
        RouteMatrixNode c = routeBuckets[idx];
        while (c != null) {
            if (c.key.equals(key)) {
                TrainInstance[] r = new TrainInstance[c.count];
                System.arraycopy(c.instances, 0, r, 0, c.count);
                return r;
            }
            c = c.next;
        }
        return new TrainInstance[0];
    }

    private void mapPnr(String pnr, PassengerNode node) {
        PL.lock();
        try {
            int idx = h(pnr);
            PnrMapNode m = new PnrMapNode(pnr, node);
            m.next = pnrBuckets[idx];
            pnrBuckets[idx] = m;
        } finally { PL.unlock(); }
    }

    PassengerNode lookupPnr(String pnr) {
        int idx = h(pnr.trim());
        PnrMapNode c = pnrBuckets[idx];
        while (c != null) {
            if (c.pnr.equalsIgnoreCase(pnr.trim())) return c.targetNode;
            c = c.next;
        }
        return null;
    }

    String bookTicketWithMethod(UserNode user, TrainInstance ti, String src, String dest, String paymentMethod) {
        ti.bookingLock.lock();
        try {
            int s = ti.getStationIndex(src), e = ti.getStationIndex(dest);
            if (s < 0 || e < 0 || s >= e) {
                return "ERROR: Invalid route bounds - " + src + " to " + dest;
            }
            double fare = ti.computeFare(s, e);

            // If paying via wallet, verify and deduct balance
            if ("WALLET".equalsIgnoreCase(paymentMethod)) {
                UL.lock();
                try {
                    if (user.walletBalance < fare) {
                        return "ERROR: Insufficient wallet balance. Required: Rs." + String.format("%.2f", fare)
                             + " | Available: Rs." + String.format("%.2f", user.walletBalance);
                    }
                    user.walletBalance -= fare;
                } finally { UL.unlock(); }
            } else {
                // External digital payment method (UPI, Card, NetBanking)
                // Simulated deduction to maintain ledger consistency
                UL.lock();
                try {
                    if (user.walletBalance >= fare) {
                        user.walletBalance -= fare;
                    }
                } finally { UL.unlock(); }
            }

            String pnr = mkPnr();
            int seat = ti.checkAvailability(s, e);
            if (seat != -1) {
                PassengerNode p = new PassengerNode(pnr, user.userId, ti.trainId, ti.trainName,
                                                    ti.date, src, dest, "CONFIRMED", fare, paymentMethod);
                p.assignedSeat = seat;
                ti.allocateSegments(s, e, seat);
                ti.insertConfirmed(p);
                mapPnr(pnr, p);
                return "CONFIRMED | Seat #" + seat + " | PNR: " + pnr
                     + " | Paid via " + paymentMethod + ": Rs." + String.format("%.2f", fare)
                     + " | " + (e - s) + " segment(s)";
            } else {
                PassengerNode p = new PassengerNode(pnr, user.userId, ti.trainId, ti.trainName,
                                                    ti.date, src, dest, "WAITING", fare, paymentMethod);
                ti.waitingList.enqueue(p);
                mapPnr(pnr, p);
                return "WAITLISTED | PNR: " + pnr
                     + " | Fare Paid via " + paymentMethod + ": Rs." + String.format("%.2f", fare)
                     + " | All " + ti.seatCapacity + " seats occupied in selected segment";
            }
        } finally { ti.bookingLock.unlock(); }
    }

    String cancelTicket(String pnr) {
        PassengerNode pass = lookupPnr(pnr);
        if (pass == null) return "ERROR: PNR not found: " + pnr;
        if (pass.status.equals("CANCELLED")) return "ERROR: Ticket already cancelled.";
        TrainInstance ti = findTrainInstance(pass.trainId, pass.date);
        if (ti == null) return "ERROR: Internal routing mismatch.";

        ti.bookingLock.lock();
        try {
            double refund = Math.max(pass.farePaid - TXN_FEE, 0);
            UL.lock();
            try {
                UserNode u = getUserNode(pass.userId);
                if (u != null) u.walletBalance += refund;
            } finally { UL.unlock(); }

            StringBuilder res = new StringBuilder();
            res.append("Cancelled PNR ").append(pnr)
               .append(" | Refunded: Rs.").append(String.format("%.2f", refund))
               .append(" (IRCTC Fee Rs.").append(String.format("%.2f", TXN_FEE)).append(" deducted)");

            if (pass.status.equals("CONFIRMED")) {
                int s = ti.getStationIndex(pass.src), e = ti.getStationIndex(pass.dest);
                ti.deallocateSegments(s, e, pass.assignedSeat);
                ti.removeConfirmed(pnr);
                pass.status = "CANCELLED";

                // Cascade waitlist promotion
                if (!ti.waitingList.isEmpty()) {
                    FIFOQueue backup = new FIFOQueue();
                    boolean promoted = false;
                    while (!ti.waitingList.isEmpty()) {
                        PassengerNode w = ti.waitingList.dequeue();
                        int ws = ti.getStationIndex(w.src), we = ti.getStationIndex(w.dest);
                        if (!promoted && w.status.equals("WAITING")) {
                            int os = ti.checkAvailability(ws, we);
                            if (os != -1) {
                                w.status = "CONFIRMED";
                                w.assignedSeat = os;
                                ti.allocateSegments(ws, we, os);
                                ti.insertConfirmed(w);
                                res.append(" | Waitlist Promotion: PNR ").append(w.pnr).append(" assigned Seat #").append(os);
                                promoted = true;
                                continue;
                            }
                        }
                        backup.enqueue(w);
                    }
                    ti.waitingList = backup;
                }
            } else {
                FIFOQueue backup = new FIFOQueue();
                while (!ti.waitingList.isEmpty()) {
                    PassengerNode w = ti.waitingList.dequeue();
                    if (!w.pnr.equals(pnr)) backup.enqueue(w);
                }
                ti.waitingList = backup;
                pass.status = "CANCELLED";
                res.append(" | Removed from Waitlist.");
            }
            return res.toString();
        } finally { ti.bookingLock.unlock(); }
    }

    TrainInstance findTrainInstance(String trainId, String date) {
        for (int i = 0; i < BUCKETS; i++) {
            RouteMatrixNode c = routeBuckets[i];
            while (c != null) {
                for (int j = 0; j < c.count; j++) {
                    TrainInstance t = c.instances[j];
                    if (t.trainId.equalsIgnoreCase(trainId) && t.date.equalsIgnoreCase(date)) return t;
                }
                c = c.next;
            }
        }
        return null;
    }

    List<PassengerNode> getBookingsForUser(String uid) {
        List<PassengerNode> l = new ArrayList<>();
        for (int i = 0; i < BUCKETS; i++) {
            PnrMapNode c = pnrBuckets[i];
            while (c != null) {
                if (c.targetNode.userId.equals(uid)) l.add(c.targetNode);
                c = c.next;
            }
        }
        l.sort((a, b) -> Long.compare(b.bookingTimestamp, a.bookingTimestamp));
        return l;
    }

    private synchronized String mkPnr() { return "PNR" + (++pnrSeed); }

    private void bootstrap() {
        String[] r1 = {"DELHI", "KANPUR", "PRAYAGRAJ", "PATNA"};
        String[] r2 = {"MUMBAI", "SURAT", "AHMEDABAD"};
        String[] r3 = {"CHENNAI", "VIJAYAWADA", "HYDERABAD", "NAGPUR"};
        String[] r4 = {"HOWRAH", "BHUBANESWAR", "VIZAG", "BANGALORE"};

        addTrainInstance(new TrainInstance("12302", "RAJDHANI EXP", "2026-11-01", r1, 2));
        addTrainInstance(new TrainInstance("12951", "AKASH EXPRESS", "2026-11-01", r2, 4));
        addTrainInstance(new TrainInstance("12951", "AKASH EXPRESS", "2026-11-02", r2, 4));
        addTrainInstance(new TrainInstance("12951", "AKASH EXPRESS", "2026-11-03", r2, 4));
        addTrainInstance(new TrainInstance("12009", "SHATABDI EXP", "2026-11-01", r2, 3));
        addTrainInstance(new TrainInstance("12009", "SHATABDI EXP", "2026-11-02", r2, 3));
        addTrainInstance(new TrainInstance("12840", "COROMANDEL EXP", "2026-11-01", r3, 5));
        addTrainInstance(new TrainInstance("12245", "HOWRAH DURONTO", "2026-11-01", r4, 4));
    }

    private String sha(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte by : b) sb.append(String.format("%02x", by));
            return sb.toString();
        } catch (Exception e) { return String.valueOf(input.hashCode()); }
    }
}

// ---------------------------------------------------------------------------
// SECTION 5 - IRCTC MODERN SEAMLESS INTERACTIVE GUI
// ---------------------------------------------------------------------------

public class ModernRailwayApp extends JFrame {

    private final CentralDataEngine engine = new CentralDataEngine();
    private UserNode currentUser;

    // Root card container
    private final JPanel rootContainer = new JPanel(new CardLayout());
    private final CardLayout cardLayout = (CardLayout) rootContainer.getLayout();

    // Portal main content tabs
    private final JPanel mainContentPanel = new JPanel(new CardLayout());
    private final CardLayout contentLayout = (CardLayout) mainContentPanel.getLayout();

    // Dynamic Navigation Bar container
    private JPanel navTabsContainer;

    // Header dynamic widgets
    private JLabel headerWalletLabel;
    private JLabel headerUserBadge;
    private JLabel liveClockLabel;

    // IRCTC Official Design Palette
    public static final Color IRCTC_NAVY      = new Color(33, 61, 119);   // #213D77 Primary Brand Blue
    public static final Color IRCTC_NAVY_DARK = new Color(20, 42, 85);    // #142A55
    public static final Color IRCTC_ORANGE    = new Color(235, 95, 20);   // #EB5F14 IRCTC Saffron/Orange
    public static final Color IRCTC_ORANGE_HOV= new Color(249, 115, 22);  // #F97316
    public static final Color IRCTC_BG        = new Color(243, 246, 251); // Clean slate travel portal background
    public static final Color CARD_BG         = Color.WHITE;
    public static final Color BORDER_COLOR    = new Color(222, 228, 238);
    public static final Color TEXT_DARK       = new Color(30, 41, 59);    // Slate-800
    public static final Color TEXT_MUTED      = new Color(100, 116, 139); // Slate-500
    public static final Color GREEN_OK        = new Color(22, 163, 74);   // Green-600
    public static final Color GREEN_BG        = new Color(220, 252, 231); // Green-100
    public static final Color AMBER_WARN      = new Color(217, 119, 6);   // Amber-600
    public static final Color AMBER_BG        = new Color(254, 243, 199); // Amber-100
    public static final Color RED_ERR         = new Color(220, 38, 38);   // Red-600
    public static final Color RED_BG          = new Color(254, 226, 226); // Red-100

    public static final Font F_TITLE  = new Font("Segoe UI", Font.BOLD, 17);
    public static final Font F_HEADER = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font F_BODY   = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font F_BOLD   = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font F_SMALL  = new Font("Segoe UI", Font.PLAIN, 11);

    // Common Indian Railway Stations for Dropdown Auto-Complete
    public static final String[] ALL_KNOWN_STATIONS = {
        "AHMEDABAD", "AGRA", "AMRITSAR", "BANGALORE", "BHOPAL", "BHUBANESWAR", 
        "CHANDIGARH", "CHENNAI", "COIMBATORE", "DEHRADUN", "DELHI", "GWALIOR", 
        "HOWRAH", "HYDERABAD", "JAIPUR", "KANPUR", "KOCHI", "KOLKATA", "KOTA", 
        "LUCKNOW", "MADURAI", "MUMBAI", "NAGPUR", "PATNA", "PRAYAGRAJ", "PUNE", 
        "RANCHI", "SOLAPUR", "SURAT", "VARANASI", "VIJAYAWADA", "VIZAG"
    };

    // =========================================================================
    // CUSTOM WIDGETS
    // =========================================================================

    static class IRCTCButton extends JButton {
        private Color normalColor, hoverColor;
        private boolean isOutline = false;

        IRCTCButton(String text, Color bg, Color hover) {
            super(text);
            this.normalColor = bg;
            this.hoverColor = hover;
            init();
        }

        static IRCTCButton primary(String text) {
            return new IRCTCButton(text, IRCTC_ORANGE, IRCTC_ORANGE_HOV);
        }

        static IRCTCButton navy(String text) {
            return new IRCTCButton(text, IRCTC_NAVY, IRCTC_NAVY_DARK);
        }

        static IRCTCButton green(String text) {
            return new IRCTCButton(text, GREEN_OK, new Color(34, 197, 94));
        }

        static IRCTCButton danger(String text) {
            return new IRCTCButton(text, RED_ERR, new Color(239, 68, 68));
        }

        static IRCTCButton outline(String text) {
            IRCTCButton b = new IRCTCButton(text, Color.WHITE, new Color(241, 245, 249));
            b.isOutline = true;
            b.setForeground(IRCTC_NAVY);
            return b;
        }

        private void init() {
            setFont(F_BOLD);
            setForeground(Color.WHITE);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(7, 14, 7, 14));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = getModel().isRollover() ? hoverColor : normalColor;
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 6, 6));

            if (isOutline) {
                g2.setColor(IRCTC_NAVY);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 6, 6));
            }
            super.paintComponent(g2);
            g2.dispose();
        }
    }

    static class CardPanel extends JPanel {
        private int arc = 10;
        CardPanel() { super(); setOpaque(false); setBackground(CARD_BG); }
        CardPanel(LayoutManager layout) { super(layout); setOpaque(false); setBackground(CARD_BG); }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, arc, arc));
            g2.setColor(BORDER_COLOR);
            g2.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, arc, arc));
            super.paintComponent(g2);
            g2.dispose();
        }
    }

    static JTextField createTextField(String val, int cols) {
        JTextField tf = new JTextField(val, cols);
        tf.setFont(F_BODY);
        tf.setForeground(TEXT_DARK);
        tf.setCaretColor(TEXT_DARK);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
            new EmptyBorder(6, 8, 6, 8)
        ));
        return tf;
    }

    static JPasswordField createPasswordField(String val, int cols) {
        JPasswordField pf = new JPasswordField(val, cols);
        pf.setFont(F_BODY);
        pf.setForeground(TEXT_DARK);
        pf.setCaretColor(TEXT_DARK);
        pf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
            new EmptyBorder(6, 8, 6, 8)
        ));
        return pf;
    }

    static JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(F_BODY);
        table.setRowHeight(34);
        table.setShowGrid(true);
        table.setGridColor(new Color(241, 245, 249));
        table.setSelectionBackground(new Color(238, 242, 255));
        table.setSelectionForeground(TEXT_DARK);

        JTableHeader th = table.getTableHeader();
        th.setFont(F_HEADER);
        th.setBackground(new Color(248, 250, 252));
        th.setForeground(IRCTC_NAVY);
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(203, 213, 225)));
        th.setPreferredSize(new Dimension(0, 36));

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object v, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(tbl, v, sel, foc, row, col);
                setBorder(new EmptyBorder(4, 10, 4, 10));
                String text = v == null ? "" : v.toString();
                if (sel) {
                    setBackground(new Color(224, 231, 255));
                } else {
                    setBackground(row % 2 == 0 ? Color.WHITE : new Color(249, 250, 251));
                }

                if (text.startsWith("CONFIRMED") || text.startsWith("SEATS AVAILABLE")) {
                    setForeground(GREEN_OK); setFont(F_BOLD);
                } else if (text.startsWith("WAITING") || text.startsWith("WAITLIST")) {
                    setForeground(AMBER_WARN); setFont(F_BOLD);
                } else if (text.startsWith("CANCELLED")) {
                    setForeground(RED_ERR); setFont(F_BOLD);
                } else {
                    setForeground(TEXT_DARK); setFont(F_BODY);
                }
                return this;
            }
        });
        return table;
    }

    // =========================================================================
    // LOGIN SCREEN
    // =========================================================================
    private JPanel buildLoginScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(IRCTC_BG);

        // Header Top Bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(IRCTC_NAVY);
        topBar.setBorder(new EmptyBorder(12, 30, 12, 30));

        JLabel brandTitle = new JLabel("INDIAN RAILWAYS PASSENGER RESERVATION SYSTEM");
        brandTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        brandTitle.setForeground(Color.WHITE);

        JLabel brandSub = new JLabel("IRCTC NextGen e-Ticketing System | Ultra-Low-Latency Core");
        brandSub.setFont(F_SMALL);
        brandSub.setForeground(new Color(210, 225, 255));

        JPanel brandLeft = new JPanel(new GridLayout(2, 1, 0, 2));
        brandLeft.setOpaque(false);
        brandLeft.add(brandTitle);
        brandLeft.add(brandSub);
        topBar.add(brandLeft, BorderLayout.WEST);

        JLabel rightEmblem = new JLabel("CRIS / IRCTC | Safety * Security * Punctuality");
        rightEmblem.setFont(F_SMALL);
        rightEmblem.setForeground(new Color(240, 245, 255));
        topBar.add(rightEmblem, BorderLayout.EAST);

        // Center Login Card Container
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setBackground(IRCTC_BG);

        CardPanel loginCard = new CardPanel();
        loginCard.setLayout(new BoxLayout(loginCard, BoxLayout.Y_AXIS));
        loginCard.setBorder(new EmptyBorder(28, 36, 28, 36));
        loginCard.setPreferredSize(new Dimension(460, 520));

        // IRCTC Logo text badge
        JPanel logoBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        logoBox.setOpaque(false);
        JLabel irctcLogo = new JLabel("IRCTC");
        irctcLogo.setFont(new Font("Segoe UI", Font.BOLD, 30));
        irctcLogo.setForeground(IRCTC_ORANGE);
        JLabel subLogo = new JLabel("e-Ticketing");
        subLogo.setFont(new Font("Segoe UI", Font.PLAIN, 24));
        subLogo.setForeground(IRCTC_NAVY);
        logoBox.add(irctcLogo);
        logoBox.add(subLogo);

        JLabel loginHdr = new JLabel("Sign In to Your IRCTC Account");
        loginHdr.setFont(F_TITLE);
        loginHdr.setForeground(TEXT_DARK);
        loginHdr.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel loginSub = new JLabel("Enter your credentials or use the designated role buttons below");
        loginSub.setFont(F_SMALL);
        loginSub.setForeground(TEXT_MUTED);
        loginSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Form Fields
        JLabel userLbl = new JLabel("Username / User ID");
        userLbl.setFont(F_BOLD);
        userLbl.setForeground(TEXT_DARK);
        userLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField userFld = createTextField("passenger1", 20);
        userFld.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JLabel passLbl = new JLabel("Password");
        passLbl.setFont(F_BOLD);
        passLbl.setForeground(TEXT_DARK);
        passLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPasswordField passFld = createPasswordField("pass123", 20);
        passFld.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        IRCTCButton loginBtn = IRCTCButton.primary("SECURE LOGIN ->");
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel statusMsg = new JLabel(" ");
        statusMsg.setFont(F_BODY);
        statusMsg.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 1-Click Fast Fill Quick Logins for Testing
        JPanel quickPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        quickPanel.setOpaque(false);
        quickPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        IRCTCButton demoPass = IRCTCButton.outline("Passenger Login");
        demoPass.setFont(F_SMALL);
        demoPass.setToolTipText("Login as regular passenger (Search & Book, PNR, My Bookings)");
        demoPass.addActionListener(e -> {
            userFld.setText("passenger1");
            passFld.setText("pass123");
            loginBtn.doClick();
        });

        IRCTCButton demoAdmin = IRCTCButton.navy("Admin Officer Login");
        demoAdmin.setFont(F_SMALL);
        demoAdmin.setToolTipText("Login as railway admin (Train Management, Route Builder, Live Matrix)");
        demoAdmin.addActionListener(e -> {
            userFld.setText("admin@irctc.res.in");
            passFld.setText("adminRoot");
            loginBtn.doClick();
        });

        quickPanel.add(demoPass);
        quickPanel.add(demoAdmin);

        ActionListener doAuth = e -> {
            String u = userFld.getText().trim();
            String p = new String(passFld.getPassword()).trim();
            UserNode node = engine.authenticate(u, p);
            if (node != null) {
                currentUser = node;
                statusMsg.setForeground(GREEN_OK);
                statusMsg.setText("Login Successful! Initializing Portal...");
                javax.swing.Timer t = new javax.swing.Timer(200, ev -> {
                    updateHeaderInfo();
                    rebuildNavigationTabs();
                    // Enforce strict role routing
                    if (currentUser.isAdmin) {
                        switchTab("ADMIN_MANAGEMENT");
                    } else {
                        switchTab("BOOK_TICKET");
                    }
                    cardLayout.show(rootContainer, "MAIN_APP");
                    ((javax.swing.Timer)ev.getSource()).stop();
                });
                t.start();
            } else {
                statusMsg.setForeground(RED_ERR);
                statusMsg.setText("Invalid User ID or Password. Check credentials.");
            }
        };

        loginBtn.addActionListener(doAuth);
        passFld.addActionListener(doAuth);

        loginCard.add(logoBox);
        loginCard.add(Box.createVerticalStrut(8));
        loginCard.add(loginHdr);
        loginCard.add(Box.createVerticalStrut(4));
        loginCard.add(loginSub);
        loginCard.add(Box.createVerticalStrut(20));
        loginCard.add(userLbl);
        loginCard.add(Box.createVerticalStrut(4));
        loginCard.add(userFld);
        loginCard.add(Box.createVerticalStrut(12));
        loginCard.add(passLbl);
        loginCard.add(Box.createVerticalStrut(4));
        loginCard.add(passFld);
        loginCard.add(Box.createVerticalStrut(20));
        loginCard.add(loginBtn);
        loginCard.add(Box.createVerticalStrut(12));
        loginCard.add(quickPanel);
        loginCard.add(Box.createVerticalStrut(10));
        loginCard.add(statusMsg);

        centerWrapper.add(loginCard);

        root.add(topBar, BorderLayout.NORTH);
        root.add(centerWrapper, BorderLayout.CENTER);
        return root;
    }

    // =========================================================================
    // MAIN APP PORTAL
    // =========================================================================
    private JPanel buildMainPortal() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(IRCTC_BG);

        // 1. TOP HEADER BRAND BAR
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(IRCTC_NAVY);
        header.setBorder(new EmptyBorder(10, 24, 10, 24));

        JPanel brandLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brandLeft.setOpaque(false);

        JLabel logoText = new JLabel("IRCTC");
        logoText.setFont(new Font("Segoe UI", Font.BOLD, 22));
        logoText.setForeground(IRCTC_ORANGE);

        JLabel portalText = new JLabel("INDIAN RAILWAYS e-TICKETING PORTAL");
        portalText.setFont(new Font("Segoe UI", Font.BOLD, 15));
        portalText.setForeground(Color.WHITE);

        brandLeft.add(logoText);
        brandLeft.add(portalText);

        // Right Info: Wallet + User Badge + Logout (NO TOGGLE ROLE BUTTON!)
        JPanel rightInfo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        rightInfo.setOpaque(false);

        liveClockLabel = new JLabel();
        liveClockLabel.setFont(F_SMALL);
        liveClockLabel.setForeground(new Color(200, 220, 255));
        updateClock();

        headerWalletLabel = new JLabel("Rs. 10,000.00");
        headerWalletLabel.setFont(F_BOLD);
        headerWalletLabel.setForeground(new Color(34, 197, 94));
        JPanel walletBadge = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        walletBadge.setBackground(new Color(20, 48, 90));
        walletBadge.setBorder(new LineBorder(new Color(50, 80, 140), 1, true));
        JLabel wIcon = new JLabel("Wallet:");
        wIcon.setFont(F_SMALL);
        wIcon.setForeground(new Color(180, 210, 255));
        walletBadge.add(wIcon);
        walletBadge.add(headerWalletLabel);

        headerUserBadge = new JLabel("passenger1");
        headerUserBadge.setFont(F_BOLD);
        headerUserBadge.setForeground(Color.WHITE);

        // Logout
        IRCTCButton logoutBtn = IRCTCButton.danger("Logout");
        logoutBtn.setFont(F_SMALL);
        logoutBtn.setBorder(new EmptyBorder(4, 12, 4, 12));
        logoutBtn.addActionListener(e -> {
            currentUser = null;
            cardLayout.show(rootContainer, "LOGIN");
        });

        rightInfo.add(liveClockLabel);
        rightInfo.add(walletBadge);
        rightInfo.add(headerUserBadge);
        rightInfo.add(logoutBtn);

        header.add(brandLeft, BorderLayout.WEST);
        header.add(rightInfo, BorderLayout.EAST);

        // 2. DYNAMIC NAVIGATION BAR (STRICT ROLE SEPARATION)
        JPanel navBar = new JPanel(new BorderLayout());
        navBar.setBackground(Color.WHITE);
        navBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        navTabsContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        navTabsContainer.setOpaque(false);
        navTabsContainer.setBorder(new EmptyBorder(2, 20, 2, 20));
        navBar.add(navTabsContainer, BorderLayout.WEST);

        // 3. NOTICE STRIP
        JPanel noticeStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 4));
        noticeStrip.setBackground(new Color(238, 242, 255));
        noticeStrip.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(224, 231, 255)));

        JLabel noticeLbl = new JLabel("IRCTC Notification: Dynamic Segment Fare Matrix active (Rs.150/hop). "
                + "Multi-Option Digital Payments (UPI, Cards, NetBanking, Wallet) with RBI Tokenization security.");
        noticeLbl.setFont(F_SMALL);
        noticeLbl.setForeground(IRCTC_NAVY);
        noticeStrip.add(noticeLbl);

        JPanel topGroup = new JPanel(new BorderLayout());
        topGroup.add(header, BorderLayout.NORTH);
        topGroup.add(navBar, BorderLayout.CENTER);
        topGroup.add(noticeStrip, BorderLayout.SOUTH);

        // 4. MAIN CONTENT TABS CONTAINER
        mainContentPanel.add(buildBookTicketTab(), "BOOK_TICKET");
        mainContentPanel.add(buildPnrLookupTab(), "PNR_LOOKUP");
        mainContentPanel.add(buildMyBookingsTab(), "MY_BOOKINGS");
        mainContentPanel.add(buildTrainManagementTab(), "ADMIN_MANAGEMENT");
        mainContentPanel.add(buildDsaInspectorTab(), "DSA_INSPECTOR");

        root.add(topGroup, BorderLayout.NORTH);
        root.add(mainContentPanel, BorderLayout.CENTER);
        return root;
    }

    // Dynamically builds the navigation tabs based on strictly verified user role
    private void rebuildNavigationTabs() {
        navTabsContainer.removeAll();
        if (currentUser == null) return;

        if (currentUser.isAdmin) {
            // ADMIN NAV: Train Management (Add/Remove) + DSA Inspector + Booking & Queries
            IRCTCButton tabMgmt = createNavButton("TRAIN MANAGEMENT (Admin: Add/Remove/Edit)", "ADMIN_MANAGEMENT");
            tabMgmt.setBackground(new Color(254, 243, 199)); // warm amber highlight
            IRCTCButton tabDsa  = createNavButton("DSA LIVE ENGINE & MATRIX", "DSA_INSPECTOR");
            IRCTCButton tabBook = createNavButton("BOOK TICKET", "BOOK_TICKET");
            IRCTCButton tabPnr  = createNavButton("PNR STATUS", "PNR_LOOKUP");
            IRCTCButton tabHist = createNavButton("MY BOOKINGS", "MY_BOOKINGS");

            navTabsContainer.add(tabMgmt);
            navTabsContainer.add(tabDsa);
            navTabsContainer.add(tabBook);
            navTabsContainer.add(tabPnr);
            navTabsContainer.add(tabHist);
        } else {
            // PASSENGER NAV: Only booking, PNR lookup, history, and read-only DSA matrix
            // TRAIN MANAGEMENT IS STRICTLY HIDDEN AND OMITTED FOR PASSENGERS
            IRCTCButton tabBook = createNavButton("BOOK TICKET", "BOOK_TICKET");
            IRCTCButton tabPnr  = createNavButton("PNR STATUS", "PNR_LOOKUP");
            IRCTCButton tabHist = createNavButton("MY BOOKINGS", "MY_BOOKINGS");
            IRCTCButton tabDsa  = createNavButton("DSA LIVE ENGINE & MATRIX", "DSA_INSPECTOR");

            navTabsContainer.add(tabBook);
            navTabsContainer.add(tabPnr);
            navTabsContainer.add(tabHist);
            navTabsContainer.add(tabDsa);
        }
        navTabsContainer.revalidate();
        navTabsContainer.repaint();
    }

    private IRCTCButton createNavButton(String title, String tabKey) {
        IRCTCButton b = IRCTCButton.outline(title);
        b.setFont(F_BOLD);
        b.addActionListener(e -> switchTab(tabKey));
        return b;
    }

    private void switchTab(String tabKey) {
        // Enforce privilege guard: if not admin and requesting admin tab, redirect
        if ("ADMIN_MANAGEMENT".equals(tabKey) && (currentUser == null || !currentUser.isAdmin)) {
            tabKey = "BOOK_TICKET";
        }
        contentLayout.show(mainContentPanel, tabKey);
        refreshTab(tabKey);
    }

    private void updateHeaderInfo() {
        if (currentUser != null) {
            headerWalletLabel.setText(String.format("Rs. %.2f", currentUser.walletBalance));
            if (currentUser.isAdmin) {
                headerUserBadge.setText("[ADMIN OFFICER] " + currentUser.userId);
                headerUserBadge.setForeground(new Color(251, 146, 60)); // warm amber
            } else {
                headerUserBadge.setText("[PASSENGER] " + currentUser.userId);
                headerUserBadge.setForeground(Color.WHITE);
            }
        }
    }

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy | HH:mm:ss");
        liveClockLabel.setText(sdf.format(new Date()));
    }

    private void refreshTab(String tabKey) {
        if ("BOOK_TICKET".equals(tabKey)) {
            executeTrainSearch();
        } else if ("MY_BOOKINGS".equals(tabKey)) {
            loadMyBookings();
        } else if ("ADMIN_MANAGEMENT".equals(tabKey)) {
            loadActiveTrains();
        } else if ("DSA_INSPECTOR".equals(tabKey)) {
            populateDsaTrainCombo();
        }
    }

    // =========================================================================
    // TAB 1: BOOK TICKET (IRCTC TRAVEL SEARCH & CARDS)
    // =========================================================================
    private JComboBox<String> searchFromCombo;
    private JComboBox<String> searchToCombo;
    private JTextField searchDateFld;
    private JPanel trainCardsListContainer;
    private JLabel searchResultsTitle;

    private JPanel buildBookTicketTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(IRCTC_BG);
        panel.setBorder(new EmptyBorder(14, 20, 14, 20));

        CardPanel searchCard = new CardPanel(new BorderLayout(0, 10));
        searchCard.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel titleLbl = new JLabel("BOOK YOUR TRAIN TICKET");
        titleLbl.setFont(F_TITLE);
        titleLbl.setForeground(IRCTC_NAVY);

        JPanel formRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        formRow.setOpaque(false);

        searchFromCombo = new JComboBox<>(ALL_KNOWN_STATIONS);
        searchFromCombo.setSelectedItem("MUMBAI");
        searchFromCombo.setFont(F_BODY);
        searchFromCombo.setPreferredSize(new Dimension(130, 34));

        searchToCombo = new JComboBox<>(ALL_KNOWN_STATIONS);
        searchToCombo.setSelectedItem("SURAT");
        searchToCombo.setFont(F_BODY);
        searchToCombo.setPreferredSize(new Dimension(130, 34));

        IRCTCButton swapBtn = IRCTCButton.outline("<->");
        swapBtn.setPreferredSize(new Dimension(46, 34));
        swapBtn.setToolTipText("Swap From and To stations");
        swapBtn.addActionListener(e -> {
            Object from = searchFromCombo.getSelectedItem();
            searchFromCombo.setSelectedItem(searchToCombo.getSelectedItem());
            searchToCombo.setSelectedItem(from);
        });

        searchDateFld = createTextField("2026-11-01", 10);
        searchDateFld.setPreferredSize(new Dimension(100, 34));

        String[] classes = {"All Classes", "General / 2S", "Sleeper (SL)", "AC 3 Tier (3A)", "AC 2 Tier (2A)"};
        JComboBox<String> classCombo = new JComboBox<>(classes);
        classCombo.setFont(F_BODY);
        classCombo.setPreferredSize(new Dimension(120, 34));

        IRCTCButton searchBtn = IRCTCButton.primary("SEARCH TRAINS ->");
        searchBtn.setPreferredSize(new Dimension(160, 34));
        searchBtn.addActionListener(e -> executeTrainSearch());

        formRow.add(new JLabel("From:"));
        formRow.add(searchFromCombo);
        formRow.add(swapBtn);
        formRow.add(new JLabel("To:"));
        formRow.add(searchToCombo);
        formRow.add(new JLabel("Date:"));
        formRow.add(searchDateFld);
        formRow.add(new JLabel("Class:"));
        formRow.add(classCombo);
        formRow.add(searchBtn);

        JPanel quickRoutes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        quickRoutes.setOpaque(false);
        JLabel qlbl = new JLabel("Popular Routes:");
        qlbl.setFont(F_SMALL);
        qlbl.setForeground(TEXT_MUTED);
        quickRoutes.add(qlbl);

        String[][] routes = {
            {"MUMBAI", "SURAT"}, {"DELHI", "PATNA"}, {"MUMBAI", "AHMEDABAD"},
            {"DELHI", "KANPUR"}, {"CHENNAI", "HYDERABAD"}, {"HOWRAH", "BANGALORE"}
        };
        for (String[] r : routes) {
            IRCTCButton qb = IRCTCButton.outline(r[0] + " -> " + r[1]);
            qb.setFont(F_SMALL);
            qb.setBorder(new EmptyBorder(3, 8, 3, 8));
            qb.addActionListener(e -> {
                searchFromCombo.setSelectedItem(r[0]);
                searchToCombo.setSelectedItem(r[1]);
                executeTrainSearch();
            });
            quickRoutes.add(qb);
        }

        searchCard.add(titleLbl, BorderLayout.NORTH);
        searchCard.add(formRow, BorderLayout.CENTER);
        searchCard.add(quickRoutes, BorderLayout.SOUTH);

        JPanel resultsWrapper = new JPanel(new BorderLayout(0, 10));
        resultsWrapper.setOpaque(false);

        searchResultsTitle = new JLabel("Available Trains");
        searchResultsTitle.setFont(F_HEADER);
        searchResultsTitle.setForeground(TEXT_DARK);
        resultsWrapper.add(searchResultsTitle, BorderLayout.NORTH);

        trainCardsListContainer = new JPanel();
        trainCardsListContainer.setLayout(new BoxLayout(trainCardsListContainer, BoxLayout.Y_AXIS));
        trainCardsListContainer.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(trainCardsListContainer);
        scrollPane.setBorder(null);
        scrollPane.setBackground(IRCTC_BG);
        scrollPane.getViewport().setBackground(IRCTC_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        resultsWrapper.add(scrollPane, BorderLayout.CENTER);

        panel.add(searchCard, BorderLayout.NORTH);
        panel.add(resultsWrapper, BorderLayout.CENTER);

        SwingUtilities.invokeLater(this::executeTrainSearch);
        return panel;
    }

    private void executeTrainSearch() {
        trainCardsListContainer.removeAll();
        String src = ((String) searchFromCombo.getSelectedItem()).trim().toUpperCase();
        String dest = ((String) searchToCombo.getSelectedItem()).trim().toUpperCase();
        String date = searchDateFld.getText().trim();

        if (src.equalsIgnoreCase(dest)) {
            searchResultsTitle.setText("Origin and Destination cannot be identical.");
            trainCardsListContainer.revalidate();
            trainCardsListContainer.repaint();
            return;
        }

        TrainInstance[] trains = engine.queryTrains(src, dest, date);
        searchResultsTitle.setText(String.format("Found %d Train(s) for %s -> %s on %s  (Dynamic Fare: Rs.150 x Segment Hops)",
                trains.length, src, dest, date));

        if (trains.length == 0) {
            CardPanel emptyCard = new CardPanel(new FlowLayout(FlowLayout.CENTER, 20, 24));
            JLabel emptyMsg = new JLabel("No direct or multi-hop trains found between " + src + " and " + dest + " on " + date + ".");
            emptyMsg.setFont(F_HEADER);
            emptyMsg.setForeground(TEXT_MUTED);
            emptyCard.add(emptyMsg);
            trainCardsListContainer.add(emptyCard);
        } else {
            for (TrainInstance ti : trains) {
                trainCardsListContainer.add(createTrainResultCard(ti, src, dest));
                trainCardsListContainer.add(Box.createVerticalStrut(10));
            }
        }
        trainCardsListContainer.revalidate();
        trainCardsListContainer.repaint();
    }

    private JPanel createTrainResultCard(TrainInstance ti, String src, String dest) {
        CardPanel card = new CardPanel(new BorderLayout(14, 10));
        card.setBorder(new EmptyBorder(12, 16, 12, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));

        int s = ti.getStationIndex(src);
        int e = ti.getStationIndex(dest);
        int hops = (s >= 0 && e >= 0) ? (e - s) : 1;
        double fare = ti.computeFare(s, e);
        int avail = ti.countAvailable(s, e);

        JPanel leftBox = new JPanel();
        leftBox.setOpaque(false);
        leftBox.setLayout(new BoxLayout(leftBox, BoxLayout.Y_AXIS));

        JLabel trainTitle = new JLabel(ti.trainId + " - " + ti.trainName);
        trainTitle.setFont(F_TITLE);
        trainTitle.setForeground(IRCTC_NAVY);

        JLabel runsOn = new JLabel("Runs On: M * T * W * T * F * S * S   |   Total Capacity: " + ti.seatCapacity + " Seats");
        runsOn.setFont(F_SMALL);
        runsOn.setForeground(TEXT_MUTED);

        JLabel routeText = new JLabel("Route: " + ti.getRouteDisplay());
        routeText.setFont(F_SMALL);
        routeText.setForeground(new Color(67, 56, 202));

        leftBox.add(trainTitle);
        leftBox.add(Box.createVerticalStrut(3));
        leftBox.add(runsOn);
        leftBox.add(Box.createVerticalStrut(3));
        leftBox.add(routeText);

        JPanel centerBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
        centerBox.setOpaque(false);

        JLabel dep = new JLabel("<html><center><b>" + src + "</b><br><font color='#64748B'>Boarding</font></center></html>");
        dep.setFont(F_BODY);

        JLabel arrow = new JLabel(String.format("<html><center>---- %d Hop(s) ----><br><font color='#16A34A'><b>Rs. %.2f</b></font></center></html>", hops, fare));
        arrow.setFont(F_BOLD);

        JLabel arr = new JLabel("<html><center><b>" + dest + "</b><br><font color='#64748B'>Destination</font></center></html>");
        arr.setFont(F_BODY);

        centerBox.add(dep);
        centerBox.add(arrow);
        centerBox.add(arr);

        JPanel rightBox = new JPanel(new GridLayout(2, 1, 0, 6));
        rightBox.setOpaque(false);

        JPanel availBadge = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        JLabel availLbl = new JLabel();
        availLbl.setFont(F_BOLD);

        if (avail > 0) {
            availBadge.setBackground(GREEN_BG);
            availBadge.setBorder(new LineBorder(new Color(187, 247, 208), 1, true));
            availLbl.setText("AVAILABLE - " + avail);
            availLbl.setForeground(GREEN_OK);
        } else {
            availBadge.setBackground(AMBER_BG);
            availBadge.setBorder(new LineBorder(new Color(254, 215, 170), 1, true));
            int waitCount = ti.waitingList.snapshot().size();
            availLbl.setText("WAITLIST - WL " + (waitCount + 1));
            availLbl.setForeground(AMBER_WARN);
        }
        availBadge.add(availLbl);

        IRCTCButton bookBtn = IRCTCButton.primary("BOOK NOW ->");
        bookBtn.setPreferredSize(new Dimension(130, 34));
        bookBtn.addActionListener(ev -> openMultiPaymentGatewayDialog(ti, src, dest, fare));

        rightBox.add(availBadge);
        rightBox.add(bookBtn);

        card.add(leftBox, BorderLayout.WEST);
        card.add(centerBox, BorderLayout.CENTER);
        card.add(rightBox, BorderLayout.EAST);
        return card;
    }

    // =========================================================================
    // MULTI-OPTION PAYMENT GATEWAY MODAL (UPI, CARDS, WALLET, NETBANKING)
    // =========================================================================
    // Enables customers to enter details, choose payment method, and optionally save
    // tokenized details safely in accordance with RBI / Government tokenization rules.
    private void openMultiPaymentGatewayDialog(TrainInstance ti, String src, String dest, double fare) {
        if (currentUser == null) return;

        JDialog payDialog = new JDialog(this, "IRCTC Secure Multi-Option Payment Gateway", true);
        payDialog.setLayout(new BorderLayout());
        payDialog.setSize(580, 560);
        payDialog.setLocationRelativeTo(this);

        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setBackground(IRCTC_BG);
        container.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Top Order Summary Card
        CardPanel summaryCard = new CardPanel(new BorderLayout(10, 4));
        summaryCard.setBorder(new EmptyBorder(12, 16, 12, 16));
        summaryCard.setBackground(new Color(238, 242, 255));

        JLabel sumHdr = new JLabel("Order Summary | Fare Payable: Rs." + String.format("%.2f", fare));
        sumHdr.setFont(F_HEADER);
        sumHdr.setForeground(IRCTC_NAVY);

        JLabel sumDetails = new JLabel(String.format("<html>Train: <b>%s - %s</b> | Route: <b>%s -> %s</b> | Date: <b>%s</b><br>"
                + "Passenger: <b>%s</b> | Wallet Balance: <b>Rs. %.2f</b></html>",
                ti.trainId, ti.trainName, src, dest, ti.date, currentUser.userId, currentUser.walletBalance));
        sumDetails.setFont(F_BODY);

        summaryCard.add(sumHdr, BorderLayout.NORTH);
        summaryCard.add(sumDetails, BorderLayout.CENTER);

        // Payment Methods Tabs Panel
        JTabbedPane payTabs = new JTabbedPane();
        payTabs.setFont(F_BOLD);

        // --- TAB 1: IRCTC WALLET ---
        JPanel walletPanel = new JPanel(new BorderLayout(0, 12));
        walletPanel.setBackground(Color.WHITE);
        walletPanel.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel wInfo = new JLabel(String.format("<html><b>IRCTC e-Wallet Payment</b><br><br>"
                + "Available Balance: <font color='#16A34A'><b>Rs. %.2f</b></font><br>"
                + "Fare to Deduct: <font color='#DC2626'><b>Rs. %.2f</b></font><br><br>"
                + "Remaining Balance after booking: <b>Rs. %.2f</b><br><br>"
                + "<i>Instant 1-click deduction with auto-refund on cancellation.</i></html>",
                currentUser.walletBalance, fare, Math.max(0, currentUser.walletBalance - fare)));
        wInfo.setFont(F_BODY);
        walletPanel.add(wInfo, BorderLayout.CENTER);

        // --- TAB 2: UPI (GPay, PhonePe, Paytm, BHIM, Cred) ---
        JPanel upiPanel = new JPanel(new BorderLayout(0, 10));
        upiPanel.setBackground(Color.WHITE);
        upiPanel.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel upiFields = new JPanel(new GridLayout(4, 1, 0, 8));
        upiFields.setOpaque(false);

        JLabel upiPrompt = new JLabel("Enter Virtual Payment Address (UPI ID):");
        upiPrompt.setFont(F_BOLD);

        String defUpi = currentUser.savedUpiId != null ? currentUser.savedUpiId : "passenger@okhdfcbank";
        JTextField upiIdFld = createTextField(defUpi, 24);

        JPanel upiAppChips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        upiAppChips.setOpaque(false);
        JLabel supLbl = new JLabel("Supported Apps:");
        supLbl.setFont(F_SMALL);
        supLbl.setForeground(TEXT_MUTED);
        upiAppChips.add(supLbl);

        String[] upiApps = {"Google Pay", "PhonePe", "Paytm", "BHIM UPI", "Cred"};
        for (String app : upiApps) {
            JLabel chip = new JLabel("[" + app + "]");
            chip.setFont(F_SMALL);
            chip.setForeground(IRCTC_NAVY);
            upiAppChips.add(chip);
        }

        JCheckBox saveUpiCb = new JCheckBox("Save UPI ID safely for future 1-click checkout (RBI Tokenized)");
        saveUpiCb.setFont(F_SMALL);
        saveUpiCb.setSelected(currentUser.savedUpiId != null);
        saveUpiCb.setOpaque(false);

        upiFields.add(upiPrompt);
        upiFields.add(upiIdFld);
        upiFields.add(upiAppChips);
        upiFields.add(saveUpiCb);
        upiPanel.add(upiFields, BorderLayout.CENTER);

        // --- TAB 3: CREDIT / DEBIT / RUPAY CARDS ---
        JPanel cardPanel = new JPanel(new BorderLayout(0, 10));
        cardPanel.setBackground(Color.WHITE);
        cardPanel.setBorder(new EmptyBorder(14, 18, 14, 18));

        JPanel cardGrid = new JPanel(new GridLayout(5, 2, 10, 8));
        cardGrid.setOpaque(false);

        String defCard = currentUser.savedCardMasked != null ? currentUser.savedCardMasked : "4532 8812 3456 8821";
        JTextField cardNoFld = createTextField(defCard, 20);
        JTextField cardHolderFld = createTextField(currentUser.savedCardHolder != null ? currentUser.savedCardHolder : "RAHUL KUMAR", 20);
        JTextField cardExpFld = createTextField(currentUser.savedCardExpiry != null ? currentUser.savedCardExpiry : "08/28", 8);
        JPasswordField cardCvvFld = createPasswordField("821", 4);

        cardGrid.add(new JLabel("Card Number:"));
        cardGrid.add(cardNoFld);
        cardGrid.add(new JLabel("Cardholder Name:"));
        cardGrid.add(cardHolderFld);
        cardGrid.add(new JLabel("Expiry (MM/YY):"));
        cardGrid.add(cardExpFld);
        cardGrid.add(new JLabel("CVV / CVC (3-digits):"));
        cardGrid.add(cardCvvFld);

        JCheckBox saveCardCb = new JCheckBox("Securely save card token as per RBI Tokenization rules (No CVV stored)");
        saveCardCb.setFont(F_SMALL);
        saveCardCb.setSelected(currentUser.savedCardMasked != null);
        saveCardCb.setOpaque(false);
        cardGrid.add(saveCardCb);
        cardGrid.add(new JLabel("")); // spacer

        cardPanel.add(cardGrid, BorderLayout.CENTER);

        // --- TAB 4: INTERNET BANKING ---
        JPanel netPanel = new JPanel(new BorderLayout(0, 10));
        netPanel.setBackground(Color.WHITE);
        netPanel.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel netGrid = new JPanel(new GridLayout(3, 1, 0, 10));
        netGrid.setOpaque(false);

        JLabel netPrompt = new JLabel("Select Your Bank for Internet Banking:");
        netPrompt.setFont(F_BOLD);

        String[] popularBanks = {
            "State Bank of India (SBI)", "HDFC Bank", "ICICI Bank", 
            "Axis Bank", "Punjab National Bank (PNB)", "Bank of Baroda", "Kotak Mahindra Bank"
        };
        JComboBox<String> bankCombo = new JComboBox<>(popularBanks);
        if (currentUser.savedBankName != null) {
            for (String b : popularBanks) if (b.contains(currentUser.savedBankName)) bankCombo.setSelectedItem(b);
        }
        bankCombo.setFont(F_BODY);

        JCheckBox saveBankCb = new JCheckBox("Remember preferred bank for faster checkout");
        saveBankCb.setFont(F_SMALL);
        saveBankCb.setSelected(currentUser.savedBankName != null);
        saveBankCb.setOpaque(false);

        netGrid.add(netPrompt);
        netGrid.add(bankCombo);
        netGrid.add(saveBankCb);
        netPanel.add(netGrid, BorderLayout.CENTER);

        payTabs.addTab("IRCTC Wallet", walletPanel);
        payTabs.addTab("UPI (GPay / PhonePe / QR)", upiPanel);
        payTabs.addTab("Credit / Debit / RuPay Card", cardPanel);
        payTabs.addTab("Internet Banking", netPanel);

        // Action Buttons Row
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        actionRow.setOpaque(false);

        IRCTCButton cancelBtn = IRCTCButton.outline("Cancel");
        cancelBtn.addActionListener(e -> payDialog.dispose());

        IRCTCButton payBtn = IRCTCButton.primary("PROCEED TO PAY Rs." + String.format("%.2f", fare) + " ->");
        payBtn.addActionListener(e -> {
            int selectedTab = payTabs.getSelectedIndex();
            String methodCode = "WALLET";

            if (selectedTab == 0) {
                methodCode = "IRCTC_WALLET";
            } else if (selectedTab == 1) {
                methodCode = "UPI";
                String upi = upiIdFld.getText().trim();
                if (upi.isEmpty() || !upi.contains("@")) {
                    JOptionPane.showMessageDialog(payDialog, "Please enter a valid UPI VPA (e.g. name@bank).", "Invalid UPI ID", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (saveUpiCb.isSelected()) {
                    currentUser.savedUpiId = upi;
                }
            } else if (selectedTab == 2) {
                methodCode = "CARD";
                String cno = cardNoFld.getText().trim();
                String cholder = cardHolderFld.getText().trim();
                String cexp = cardExpFld.getText().trim();
                String cvv = new String(cardCvvFld.getPassword()).trim();
                if (cno.length() < 12 || cvv.length() < 3) {
                    JOptionPane.showMessageDialog(payDialog, "Please enter valid card number and CVV.", "Invalid Card Details", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (saveCardCb.isSelected()) {
                    // RBI Tokenization compliance: mask all except last 4 digits
                    String clean = cno.replaceAll("\\s+", "");
                    String last4 = clean.length() >= 4 ? clean.substring(clean.length() - 4) : "8821";
                    currentUser.savedCardMasked = "RuPay **** **** **** " + last4;
                    currentUser.savedCardExpiry = cexp;
                    currentUser.savedCardHolder = cholder;
                }
            } else if (selectedTab == 3) {
                methodCode = "NETBANKING";
                String bank = (String) bankCombo.getSelectedItem();
                if (saveBankCb.isSelected()) {
                    currentUser.savedBankName = bank;
                }
            }

            payDialog.dispose();

            // Execute reservation through CentralDataEngine
            String result = engine.bookTicketWithMethod(currentUser, ti, src, dest, methodCode);
            updateHeaderInfo();
            executeTrainSearch();

            if (result.startsWith("CONFIRMED")) {
                JOptionPane.showMessageDialog(this,
                    "Payment Successful via " + methodCode + "!\n\n" + result,
                    "Reservation Confirmed (ERS Generated)", JOptionPane.INFORMATION_MESSAGE);
            } else if (result.startsWith("WAITLISTED")) {
                JOptionPane.showMessageDialog(this,
                    "Payment Authorized via " + methodCode + "!\n\n" + result,
                    "Waitlist Ticket Issued (FIFO Queue)", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, result, "Booking Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        actionRow.add(cancelBtn);
        actionRow.add(payBtn);

        container.add(summaryCard, BorderLayout.NORTH);
        container.add(payTabs, BorderLayout.CENTER);
        container.add(actionRow, BorderLayout.SOUTH);

        payDialog.add(container);
        payDialog.setVisible(true);
    }

    // =========================================================================
    // TAB 2: PNR ENQUIRY (BOARDING PASS)
    // =========================================================================
    private JTextField pnrInputFld;
    private JPanel pnrResultContainer;

    private JPanel buildPnrLookupTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(IRCTC_BG);
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));

        CardPanel searchCard = new CardPanel(new FlowLayout(FlowLayout.LEFT, 14, 10));
        searchCard.setBorder(new EmptyBorder(8, 14, 8, 14));

        JLabel pnrLbl = new JLabel("Passenger PNR Number:");
        pnrLbl.setFont(F_HEADER);
        pnrLbl.setForeground(IRCTC_NAVY);

        pnrInputFld = createTextField("", 16);
        pnrInputFld.setPreferredSize(new Dimension(170, 34));

        IRCTCButton getStatusBtn = IRCTCButton.primary("GET PNR STATUS ->");
        getStatusBtn.setPreferredSize(new Dimension(160, 34));
        getStatusBtn.addActionListener(e -> executePnrLookup());

        searchCard.add(pnrLbl);
        searchCard.add(pnrInputFld);
        searchCard.add(getStatusBtn);

        pnrResultContainer = new JPanel(new BorderLayout());
        pnrResultContainer.setOpaque(false);

        panel.add(searchCard, BorderLayout.NORTH);
        panel.add(pnrResultContainer, BorderLayout.CENTER);
        return panel;
    }

    private void executePnrLookup() {
        pnrResultContainer.removeAll();
        String pnr = pnrInputFld.getText().trim();
        if (pnr.isEmpty()) {
            pnrResultContainer.revalidate();
            pnrResultContainer.repaint();
            return;
        }

        PassengerNode node = engine.lookupPnr(pnr);
        if (node == null) {
            CardPanel errCard = new CardPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
            JLabel errMsg = new JLabel("No reservation record found in PnrMap for PNR: " + pnr);
            errMsg.setFont(F_HEADER);
            errMsg.setForeground(RED_ERR);
            errCard.add(errMsg);
            pnrResultContainer.add(errCard, BorderLayout.NORTH);
        } else {
            pnrResultContainer.add(createElectronicReservationSlip(node), BorderLayout.NORTH);
        }
        pnrResultContainer.revalidate();
        pnrResultContainer.repaint();
    }

    private JPanel createElectronicReservationSlip(PassengerNode p) {
        CardPanel slip = new CardPanel(new BorderLayout(0, 12));
        slip.setBorder(new EmptyBorder(16, 20, 16, 20));
        slip.setMaximumSize(new Dimension(750, 420));

        JPanel slipHeader = new JPanel(new BorderLayout());
        slipHeader.setBackground(IRCTC_NAVY);
        slipHeader.setBorder(new EmptyBorder(8, 14, 8, 14));

        JLabel ersTitle = new JLabel("Electronic Reservation Slip (ERS) | Passenger Current Status");
        ersTitle.setFont(F_HEADER);
        ersTitle.setForeground(Color.WHITE);

        JLabel pnrBadge = new JLabel("PNR: " + p.pnr + "   ");
        pnrBadge.setFont(F_TITLE);
        pnrBadge.setForeground(new Color(251, 146, 60));

        slipHeader.add(ersTitle, BorderLayout.WEST);
        slipHeader.add(pnrBadge, BorderLayout.EAST);

        JPanel body = new JPanel(new GridLayout(4, 2, 14, 8));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(10, 8, 10, 8));

        body.add(createSlipField("Train Number & Name", p.trainId + " / " + p.trainName));
        body.add(createSlipField("Date of Journey", p.date));
        body.add(createSlipField("From (Boarding)", p.src));
        body.add(createSlipField("To (Destination)", p.dest));
        body.add(createSlipField("Payment Method Used", p.paymentMethod != null ? p.paymentMethod : "IRCTC_WALLET"));
        body.add(createSlipField("Total Fare Paid", String.format("Rs. %.2f", p.farePaid)));

        JLabel statusVal = new JLabel(p.status);
        statusVal.setFont(F_TITLE);
        if ("CONFIRMED".equals(p.status)) statusVal.setForeground(GREEN_OK);
        else if ("WAITING".equals(p.status)) statusVal.setForeground(AMBER_WARN);
        else statusVal.setForeground(RED_ERR);

        JPanel statusBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusBox.setOpaque(false);
        JLabel sLbl = new JLabel("Booking Status:  ");
        sLbl.setFont(F_BOLD);
        sLbl.setForeground(TEXT_MUTED);
        statusBox.add(sLbl);
        statusBox.add(statusVal);

        JLabel seatInfo = new JLabel(p.assignedSeat >= 0 ? "Coach S1 | Seat #" + p.assignedSeat : "Seat Allocation Pending (Waitlist)");
        seatInfo.setFont(F_HEADER);
        seatInfo.setForeground(IRCTC_NAVY);

        body.add(statusBox);
        body.add(seatInfo);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        footer.setOpaque(false);

        if (!"CANCELLED".equals(p.status)) {
            IRCTCButton cancelBtn = IRCTCButton.danger("Cancel This Ticket (Auto-Refund)");
            cancelBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to cancel PNR " + p.pnr + "?\n"
                    + "Refund of Rs." + String.format("%.2f", Math.max(0, p.farePaid - CentralDataEngine.TXN_FEE))
                    + " will be credited back to wallet.",
                    "Confirm Cancellation", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    String cancelResult = engine.cancelTicket(p.pnr);
                    updateHeaderInfo();
                    JOptionPane.showMessageDialog(this, cancelResult, "Ticket Cancelled", JOptionPane.INFORMATION_MESSAGE);
                    executePnrLookup();
                    refreshTab("MY_BOOKINGS");
                }
            });
            footer.add(cancelBtn);
        }

        slip.add(slipHeader, BorderLayout.NORTH);
        slip.add(body, BorderLayout.CENTER);
        slip.add(footer, BorderLayout.SOUTH);
        return slip;
    }

    private JPanel createSlipField(String label, String value) {
        JPanel p = new JPanel(new BorderLayout(4, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(F_SMALL);
        l.setForeground(TEXT_MUTED);
        JLabel v = new JLabel(value);
        v.setFont(F_BOLD);
        v.setForeground(TEXT_DARK);
        p.add(l, BorderLayout.NORTH);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    // =========================================================================
    // TAB 3: MY BOOKINGS
    // =========================================================================
    private DefaultTableModel myBookingsTableModel;

    private JPanel buildMyBookingsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(IRCTC_BG);
        panel.setBorder(new EmptyBorder(14, 20, 14, 20));

        CardPanel topCard = new CardPanel(new BorderLayout());
        topCard.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel title = new JLabel("My Ticket Booking History");
        title.setFont(F_TITLE);
        title.setForeground(IRCTC_NAVY);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControls.setOpaque(false);

        IRCTCButton refBtn = IRCTCButton.outline("Refresh Bookings");
        refBtn.addActionListener(e -> loadMyBookings());

        rightControls.add(refBtn);
        topCard.add(title, BorderLayout.WEST);
        topCard.add(rightControls, BorderLayout.EAST);

        String[] cols = {"PNR", "Train ID", "Train Name", "Date", "From", "To", "Status", "Seat", "Fare (Rs.)", "Payment Method", "Booked At"};
        myBookingsTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = createStyledTable(myBookingsTableModel);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1, true));

        CardPanel bottomBar = new CardPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        bottomBar.setBorder(new EmptyBorder(4, 14, 4, 14));

        JLabel hint = new JLabel("Select a booking row to cancel or view refund details");
        hint.setFont(F_SMALL);
        hint.setForeground(TEXT_MUTED);

        IRCTCButton cancelSelectedBtn = IRCTCButton.danger("Cancel Selected Ticket");
        cancelSelectedBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Please select a booking row from the table first.", "Selection Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String pnr = (String) myBookingsTableModel.getValueAt(row, 0);
            String status = (String) myBookingsTableModel.getValueAt(row, 6);
            if ("CANCELLED".equals(status)) {
                JOptionPane.showMessageDialog(this, "This ticket is already cancelled.", "Notice", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int conf = JOptionPane.showConfirmDialog(this, "Confirm cancellation for PNR: " + pnr + "?", "Confirm Cancel", JOptionPane.YES_NO_OPTION);
            if (conf == JOptionPane.YES_OPTION) {
                String res = engine.cancelTicket(pnr);
                updateHeaderInfo();
                loadMyBookings();
                JOptionPane.showMessageDialog(this, res, "Cancellation Status", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        bottomBar.add(hint);
        bottomBar.add(cancelSelectedBtn);

        panel.add(topCard, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
    }

    private void loadMyBookings() {
        myBookingsTableModel.setRowCount(0);
        if (currentUser == null) return;
        List<PassengerNode> list = engine.getBookingsForUser(currentUser.userId);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        for (PassengerNode p : list) {
            myBookingsTableModel.addRow(new Object[]{
                p.pnr, p.trainId, p.trainName, p.date, p.src, p.dest, p.status,
                p.assignedSeat < 0 ? "--" : String.valueOf(p.assignedSeat),
                String.format("%.2f", p.farePaid),
                p.paymentMethod != null ? p.paymentMethod : "WALLET",
                sdf.format(new Date(p.bookingTimestamp))
            });
        }
    }

    // =========================================================================
    // TAB 4: ADMIN TRAIN MANAGEMENT & INTERACTIVE ROUTE BUILDER
    // =========================================================================
    // Strictly accessible to Admin only. Allows setting Start & End stations, then
    // adding/removing/reordering intermediate stoppages via add icon, with edit/remove later.
    private DefaultTableModel activeTrainsTableModel;
    private JTextField newTrainIdFld, newTrainNameFld, newTrainDateFld, newTrainCapFld;
    private JComboBox<String> originStationCombo, terminusStationCombo, intermediateAddCombo;
    private DefaultListModel<String> intermediateStopsListModel;
    private JList<String> intermediateStopsList;
    private JLabel routeLivePreviewLbl;
    private JLabel addTrainStatusLbl;
    private IRCTCButton submitTrainBtn;
    private boolean isEditingMode = false;
    private String editingOriginalId = null;
    private String editingOriginalDate = null;

    private JPanel buildTrainManagementTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(IRCTC_BG);
        panel.setBorder(new EmptyBorder(14, 20, 14, 20));

        CardPanel banner = new CardPanel(new BorderLayout(14, 2));
        banner.setBorder(new EmptyBorder(10, 16, 10, 16));
        banner.setBackground(new Color(255, 247, 237));

        JLabel bTitle = new JLabel("IRCTC Central Train Management & Route Matrix Administration (Admin Access Only)");
        bTitle.setFont(F_TITLE);
        bTitle.setForeground(IRCTC_ORANGE);

        JLabel bSub = new JLabel("Configure Origin & Terminus stations, inject intermediate stops with [+] icon, or remove/edit existing services.");
        bSub.setFont(F_BODY);
        bSub.setForeground(TEXT_DARK);

        banner.add(bTitle, BorderLayout.NORTH);
        banner.add(bSub, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        splitPane.setDividerLocation(470);
        splitPane.setDividerSize(8);

        // ----------------- LEFT: INTERACTIVE ROUTE BUILDER -----------------
        CardPanel builderCard = new CardPanel(new BorderLayout(0, 8));
        builderCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel addHeader = new JLabel("Train Route Configurator (Start, End & Intermediate Stops)");
        addHeader.setFont(F_HEADER);
        addHeader.setForeground(IRCTC_NAVY);

        JPanel mainForm = new JPanel();
        mainForm.setLayout(new BoxLayout(mainForm, BoxLayout.Y_AXIS));
        mainForm.setOpaque(false);

        // Top Attributes Grid
        JPanel attrGrid = new JPanel(new GridLayout(4, 2, 8, 8));
        attrGrid.setOpaque(false);

        newTrainIdFld = createTextField("12431", 10);
        newTrainNameFld = createTextField("TRIVANDRUM RAJDHANI", 16);
        newTrainDateFld = createTextField("2026-11-01", 10);
        newTrainCapFld = createTextField("4", 6);

        attrGrid.add(new JLabel("Train ID / No:"));
        attrGrid.add(newTrainIdFld);
        attrGrid.add(new JLabel("Train Name:"));
        attrGrid.add(newTrainNameFld);
        attrGrid.add(new JLabel("Date (YYYY-MM-DD):"));
        attrGrid.add(newTrainDateFld);
        attrGrid.add(new JLabel("Seat Capacity:"));
        attrGrid.add(newTrainCapFld);

        // Origin and Terminus Section
        JPanel terminalGrid = new JPanel(new GridLayout(2, 2, 8, 8));
        terminalGrid.setOpaque(false);
        terminalGrid.setBorder(new TitledBorder(new LineBorder(BORDER_COLOR), "1. Route Endpoints (Origin & Terminus)", TitledBorder.LEFT, TitledBorder.TOP, F_BOLD, IRCTC_NAVY));

        originStationCombo = new JComboBox<>(ALL_KNOWN_STATIONS);
        originStationCombo.setSelectedItem("DELHI");
        originStationCombo.setEditable(true);
        originStationCombo.addActionListener(e -> updateRoutePreview());

        terminusStationCombo = new JComboBox<>(ALL_KNOWN_STATIONS);
        terminusStationCombo.setSelectedItem("MUMBAI");
        terminusStationCombo.setEditable(true);
        terminusStationCombo.addActionListener(e -> updateRoutePreview());

        terminalGrid.add(new JLabel("Start Source Station:"));
        terminalGrid.add(originStationCombo);
        terminalGrid.add(new JLabel("End Terminus Station:"));
        terminalGrid.add(terminusStationCombo);

        // Intermediate Stops Section (Interactive Add / Remove / Reorder)
        JPanel stopsPanel = new JPanel(new BorderLayout(0, 6));
        stopsPanel.setOpaque(false);
        stopsPanel.setBorder(new TitledBorder(new LineBorder(BORDER_COLOR), "2. Intermediate Stoppages (Select & Add Between)", TitledBorder.LEFT, TitledBorder.TOP, F_BOLD, IRCTC_NAVY));

        JPanel addStopRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        addStopRow.setOpaque(false);

        intermediateAddCombo = new JComboBox<>(ALL_KNOWN_STATIONS);
        intermediateAddCombo.setSelectedItem("KOTA");
        intermediateAddCombo.setEditable(true);
        intermediateAddCombo.setPreferredSize(new Dimension(150, 30));

        IRCTCButton addStopBtn = IRCTCButton.primary("[+] Add Stop");
        addStopBtn.setPreferredSize(new Dimension(110, 30));
        addStopBtn.addActionListener(e -> {
            String st = ((String) intermediateAddCombo.getSelectedItem()).trim().toUpperCase();
            String orig = ((String) originStationCombo.getSelectedItem()).trim().toUpperCase();
            String term = ((String) terminusStationCombo.getSelectedItem()).trim().toUpperCase();

            if (st.isEmpty()) return;
            if (st.equals(orig) || st.equals(term)) {
                JOptionPane.showMessageDialog(this, "Station " + st + " is already designated as Origin or Terminus.", "Duplicate Endpoint", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (intermediateStopsListModel.contains(st)) {
                JOptionPane.showMessageDialog(this, "Station " + st + " is already in the intermediate stops list.", "Duplicate Stoppage", JOptionPane.WARNING_MESSAGE);
                return;
            }
            intermediateStopsListModel.addElement(st);
            updateRoutePreview();
        });

        IRCTCButton removeStopBtn = IRCTCButton.danger("[-] Remove");
        removeStopBtn.setPreferredSize(new Dimension(100, 30));
        removeStopBtn.addActionListener(e -> {
            int sel = intermediateStopsList.getSelectedIndex();
            if (sel >= 0) {
                intermediateStopsListModel.remove(sel);
                updateRoutePreview();
            } else {
                JOptionPane.showMessageDialog(this, "Please select an intermediate stop from the list to remove.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        IRCTCButton moveUpBtn = IRCTCButton.outline("Up");
        moveUpBtn.setPreferredSize(new Dimension(55, 30));
        moveUpBtn.addActionListener(e -> {
            int sel = intermediateStopsList.getSelectedIndex();
            if (sel > 0) {
                String item = intermediateStopsListModel.remove(sel);
                intermediateStopsListModel.add(sel - 1, item);
                intermediateStopsList.setSelectedIndex(sel - 1);
                updateRoutePreview();
            }
        });

        IRCTCButton moveDownBtn = IRCTCButton.outline("Down");
        moveDownBtn.setPreferredSize(new Dimension(55, 30));
        moveDownBtn.addActionListener(e -> {
            int sel = intermediateStopsList.getSelectedIndex();
            if (sel >= 0 && sel < intermediateStopsListModel.size() - 1) {
                String item = intermediateStopsListModel.remove(sel);
                intermediateStopsListModel.add(sel + 1, item);
                intermediateStopsList.setSelectedIndex(sel + 1);
                updateRoutePreview();
            }
        });

        addStopRow.add(new JLabel("Station:"));
        addStopRow.add(intermediateAddCombo);
        addStopRow.add(addStopBtn);
        addStopRow.add(removeStopBtn);
        addStopRow.add(moveUpBtn);
        addStopRow.add(moveDownBtn);

        intermediateStopsListModel = new DefaultListModel<>();
        intermediateStopsListModel.addElement("KOTA");
        intermediateStopsListModel.addElement("VADODARA");

        intermediateStopsList = new JList<>(intermediateStopsListModel);
        intermediateStopsList.setFont(F_BOLD);
        intermediateStopsList.setVisibleRowCount(4);
        JScrollPane listScroll = new JScrollPane(intermediateStopsList);
        listScroll.setPreferredSize(new Dimension(0, 90));
        listScroll.setBorder(new LineBorder(BORDER_COLOR, 1, true));

        stopsPanel.add(addStopRow, BorderLayout.NORTH);
        stopsPanel.add(listScroll, BorderLayout.CENTER);

        // Live Route Preview
        JPanel previewCard = new JPanel(new BorderLayout(4, 2));
        previewCard.setOpaque(false);
        previewCard.setBorder(new EmptyBorder(4, 2, 4, 2));
        routeLivePreviewLbl = new JLabel();
        routeLivePreviewLbl.setFont(F_BOLD);
        routeLivePreviewLbl.setForeground(new Color(67, 56, 202));
        previewCard.add(new JLabel("Generated Spatial Route Chain:"), BorderLayout.NORTH);
        previewCard.add(routeLivePreviewLbl, BorderLayout.CENTER);

        mainForm.add(attrGrid);
        mainForm.add(Box.createVerticalStrut(8));
        mainForm.add(terminalGrid);
        mainForm.add(Box.createVerticalStrut(8));
        mainForm.add(stopsPanel);
        mainForm.add(Box.createVerticalStrut(6));
        mainForm.add(previewCard);

        // South Action Controls
        JPanel addSouth = new JPanel(new GridLayout(3, 1, 0, 4));
        addSouth.setOpaque(false);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnRow.setOpaque(false);

        submitTrainBtn = IRCTCButton.primary("[+] INJECT TRAIN INTO MATRIX");
        submitTrainBtn.setPreferredSize(new Dimension(240, 36));
        submitTrainBtn.addActionListener(e -> executeSaveTrain());

        IRCTCButton cancelEditBtn = IRCTCButton.outline("Cancel Edit");
        cancelEditBtn.addActionListener(e -> resetBuilderForm());

        btnRow.add(submitTrainBtn);
        btnRow.add(cancelEditBtn);

        addTrainStatusLbl = new JLabel("Configure stations and click Inject.");
        addTrainStatusLbl.setFont(F_SMALL);
        addTrainStatusLbl.setForeground(TEXT_MUTED);
        addTrainStatusLbl.setHorizontalAlignment(SwingConstants.CENTER);

        addSouth.add(btnRow);
        addSouth.add(addTrainStatusLbl);

        builderCard.add(addHeader, BorderLayout.NORTH);
        builderCard.add(mainForm, BorderLayout.CENTER);
        builderCard.add(addSouth, BorderLayout.SOUTH);

        // ----------------- RIGHT: ACTIVE TRAINS DIRECTORY (REMOVE & EDIT) -----------------
        CardPanel rightCard = new CardPanel(new BorderLayout(0, 8));
        rightCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel dirHeader = new JPanel(new BorderLayout());
        dirHeader.setOpaque(false);

        JLabel dirTitle = new JLabel("Active Trains Directory (" + engine.getAllUniqueTrains().size() + " trains in Matrix)");
        dirTitle.setFont(F_HEADER);
        dirTitle.setForeground(IRCTC_NAVY);

        IRCTCButton refreshDirBtn = IRCTCButton.outline("Refresh List");
        refreshDirBtn.addActionListener(e -> loadActiveTrains());

        dirHeader.add(dirTitle, BorderLayout.WEST);
        dirHeader.add(refreshDirBtn, BorderLayout.EAST);

        String[] cols = {"Train No", "Name", "Date", "Full Route Path", "Capacity", "Confirmed", "Waiting"};
        activeTrainsTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable dirTable = createStyledTable(activeTrainsTableModel);
        JScrollPane dirScroll = new JScrollPane(dirTable);
        dirScroll.setBorder(new LineBorder(BORDER_COLOR, 1, true));

        // Bottom Controls: Edit Train + Remove / Cancel Train + View Roster
        JPanel removeControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        removeControls.setOpaque(false);

        IRCTCButton editSelectedBtn = IRCTCButton.outline("Edit Selected Train");
        editSelectedBtn.addActionListener(e -> {
            int row = dirTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Please click on a train row in the table to edit it.", "Selection Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String tId = (String) activeTrainsTableModel.getValueAt(row, 0);
            String tDate = (String) activeTrainsTableModel.getValueAt(row, 2);
            TrainInstance ti = engine.findTrainInstance(tId, tDate);
            if (ti != null) {
                loadTrainIntoBuilder(ti);
            }
        });

        IRCTCButton removeSelectedBtn = IRCTCButton.danger("[x] Remove / Cancel Train");
        removeSelectedBtn.addActionListener(e -> {
            int row = dirTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Please click on a train row in the table above to remove it.", "Selection Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String tId = (String) activeTrainsTableModel.getValueAt(row, 0);
            String tDate = (String) activeTrainsTableModel.getValueAt(row, 2);
            String tName = (String) activeTrainsTableModel.getValueAt(row, 1);

            int conf = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to PERMANENTLY REMOVE Train " + tId + " (" + tName + ") on " + tDate + "?\n"
                + "This will purge all associated station-pair entries from the Central Route Matrix.",
                "Confirm Train Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (conf == JOptionPane.YES_OPTION) {
                boolean ok = engine.removeTrainInstance(tId, tDate);
                if (ok) {
                    JOptionPane.showMessageDialog(this, "Train " + tId + " has been successfully removed from all Route Matrix buckets.", "Train Removed", JOptionPane.INFORMATION_MESSAGE);
                    loadActiveTrains();
                    refreshTab("BOOK_TICKET");
                    refreshTab("DSA_INSPECTOR");
                } else {
                    JOptionPane.showMessageDialog(this, "Could not remove train with ID " + tId, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        IRCTCButton viewRosterBtn = IRCTCButton.navy("View Roster & Matrix");
        viewRosterBtn.addActionListener(e -> {
            int row = dirTable.getSelectedRow();
            if (row >= 0) {
                String tId = (String) activeTrainsTableModel.getValueAt(row, 0);
                String tDate = (String) activeTrainsTableModel.getValueAt(row, 2);
                dsaTrainSelectCombo.setSelectedItem(tId + " - " + tDate);
            }
            switchTab("DSA_INSPECTOR");
        });

        removeControls.add(editSelectedBtn);
        removeControls.add(removeSelectedBtn);
        removeControls.add(viewRosterBtn);

        rightCard.add(dirHeader, BorderLayout.NORTH);
        rightCard.add(dirScroll, BorderLayout.CENTER);
        rightCard.add(removeControls, BorderLayout.SOUTH);

        splitPane.setLeftComponent(builderCard);
        splitPane.setRightComponent(rightCard);

        panel.add(banner, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);

        updateRoutePreview();
        return panel;
    }

    private void updateRoutePreview() {
        if (originStationCombo == null || terminusStationCombo == null || intermediateStopsListModel == null) return;
        String orig = ((String) originStationCombo.getSelectedItem()).trim().toUpperCase();
        String term = ((String) terminusStationCombo.getSelectedItem()).trim().toUpperCase();

        StringBuilder sb = new StringBuilder();
        sb.append(orig);
        for (int i = 0; i < intermediateStopsListModel.size(); i++) {
            sb.append(" -> ").append(intermediateStopsListModel.get(i));
        }
        sb.append(" -> ").append(term);

        int totalStations = 2 + intermediateStopsListModel.size();
        int totalSegments = totalStations - 1;
        int permutations = (totalStations * (totalStations - 1)) / 2;

        routeLivePreviewLbl.setText(String.format("<html>%s<br><font color='#16A34A'>%d Segments | %d Station Pairs in Matrix | Rs.150/hop</font></html>",
                sb.toString(), totalSegments, permutations));
    }

    private void loadTrainIntoBuilder(TrainInstance ti) {
        isEditingMode = true;
        editingOriginalId = ti.trainId;
        editingOriginalDate = ti.date;

        newTrainIdFld.setText(ti.trainId);
        newTrainNameFld.setText(ti.trainName);
        newTrainDateFld.setText(ti.date);
        newTrainCapFld.setText(String.valueOf(ti.seatCapacity));

        originStationCombo.setSelectedItem(ti.stations[0]);
        terminusStationCombo.setSelectedItem(ti.stations[ti.stations.length - 1]);

        intermediateStopsListModel.clear();
        for (int i = 1; i < ti.stations.length - 1; i++) {
            intermediateStopsListModel.addElement(ti.stations[i]);
        }

        submitTrainBtn.setText("[*] SAVE UPDATED TRAIN");
        addTrainStatusLbl.setForeground(IRCTC_ORANGE);
        addTrainStatusLbl.setText("Editing Train " + ti.trainId + ". Click Save to update Matrix.");
        updateRoutePreview();
    }

    private void resetBuilderForm() {
        isEditingMode = false;
        editingOriginalId = null;
        editingOriginalDate = null;

        newTrainIdFld.setText("12431");
        newTrainNameFld.setText("TRIVANDRUM RAJDHANI");
        newTrainDateFld.setText("2026-11-01");
        newTrainCapFld.setText("4");

        originStationCombo.setSelectedItem("DELHI");
        terminusStationCombo.setSelectedItem("MUMBAI");

        intermediateStopsListModel.clear();
        intermediateStopsListModel.addElement("KOTA");
        intermediateStopsListModel.addElement("VADODARA");

        submitTrainBtn.setText("[+] INJECT TRAIN INTO MATRIX");
        addTrainStatusLbl.setForeground(TEXT_MUTED);
        addTrainStatusLbl.setText("Form reset. Ready to inject new train.");
        updateRoutePreview();
    }

    private void executeSaveTrain() {
        try {
            String id = newTrainIdFld.getText().trim();
            String name = newTrainNameFld.getText().trim();
            String date = newTrainDateFld.getText().trim();
            String capStr = newTrainCapFld.getText().trim();

            String orig = ((String) originStationCombo.getSelectedItem()).trim().toUpperCase();
            String term = ((String) terminusStationCombo.getSelectedItem()).trim().toUpperCase();

            if (id.isEmpty() || name.isEmpty() || date.isEmpty() || capStr.isEmpty() || orig.isEmpty() || term.isEmpty()) {
                addTrainStatusLbl.setForeground(RED_ERR);
                addTrainStatusLbl.setText("All fields, Origin, and Terminus are required.");
                return;
            }

            if (orig.equalsIgnoreCase(term)) {
                addTrainStatusLbl.setForeground(RED_ERR);
                addTrainStatusLbl.setText("Origin and Terminus cannot be identical.");
                return;
            }

            int cap = Integer.parseInt(capStr);
            if (cap <= 0) {
                addTrainStatusLbl.setForeground(RED_ERR);
                addTrainStatusLbl.setText("Capacity must be > 0.");
                return;
            }

            // Construct full stations array
            int total = 2 + intermediateStopsListModel.size();
            String[] stns = new String[total];
            stns[0] = orig;
            for (int i = 0; i < intermediateStopsListModel.size(); i++) {
                stns[i + 1] = intermediateStopsListModel.get(i);
            }
            stns[total - 1] = term;

            // If editing, purge old instance first
            if (isEditingMode && editingOriginalId != null) {
                engine.removeTrainInstance(editingOriginalId, editingOriginalDate);
            }

            TrainInstance ti = new TrainInstance(id, name, date, stns, cap);
            engine.addTrainInstance(ti);

            addTrainStatusLbl.setForeground(GREEN_OK);
            addTrainStatusLbl.setText("Train " + id + " saved successfully into Route Matrix!");

            loadActiveTrains();
            refreshTab("BOOK_TICKET");
            refreshTab("DSA_INSPECTOR");

            JOptionPane.showMessageDialog(this,
                "Train " + id + " (" + name + ") has been configured in the Route Matrix!\n"
                + "Route: " + String.join(" -> ", stns) + "\n"
                + "Capacity: " + cap + " Seats | Date: " + date,
                isEditingMode ? "Train Updated Successfully" : "Train Added Successfully",
                JOptionPane.INFORMATION_MESSAGE);

            resetBuilderForm();

        } catch (NumberFormatException nfe) {
            addTrainStatusLbl.setForeground(RED_ERR);
            addTrainStatusLbl.setText("Invalid capacity number.");
        } catch (Exception ex) {
            addTrainStatusLbl.setForeground(RED_ERR);
            addTrainStatusLbl.setText("Error: " + ex.getMessage());
        }
    }

    private void loadActiveTrains() {
        activeTrainsTableModel.setRowCount(0);
        List<TrainInstance> list = engine.getAllUniqueTrains();
        for (TrainInstance ti : list) {
            int conf = ti.confirmedSnapshot().size();
            int wait = ti.waitingList.snapshot().size();
            activeTrainsTableModel.addRow(new Object[]{
                ti.trainId,
                ti.trainName,
                ti.date,
                ti.getRouteDisplay(),
                ti.seatCapacity + " Seats",
                conf,
                wait
            });
        }
    }

    // =========================================================================
    // TAB 5: DSA LIVE ENGINE & 2-D SEGMENT MATRIX INSPECTOR
    // =========================================================================
    private JComboBox<String> dsaTrainSelectCombo;
    private JPanel dsaMatrixGridContainer;
    private DefaultTableModel dsaRosterTableModel;
    private DefaultTableModel dsaWaitlistTableModel;
    private JLabel dsaMetricsLbl;

    private JPanel buildDsaInspectorTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(IRCTC_BG);
        panel.setBorder(new EmptyBorder(14, 20, 14, 20));

        CardPanel topBar = new CardPanel(new FlowLayout(FlowLayout.LEFT, 14, 6));
        topBar.setBorder(new EmptyBorder(6, 14, 6, 14));

        JLabel pickLbl = new JLabel("Select Train to Inspect Real-Time DSA State:");
        pickLbl.setFont(F_HEADER);
        pickLbl.setForeground(IRCTC_NAVY);

        dsaTrainSelectCombo = new JComboBox<>();
        dsaTrainSelectCombo.setFont(F_BODY);
        dsaTrainSelectCombo.setPreferredSize(new Dimension(280, 32));
        dsaTrainSelectCombo.addActionListener(e -> updateDsaVisualizer());

        IRCTCButton refBtn = IRCTCButton.outline("Refresh DSA State");
        refBtn.addActionListener(e -> populateDsaTrainCombo());

        topBar.add(pickLbl);
        topBar.add(dsaTrainSelectCombo);
        topBar.add(refBtn);

        JPanel centerGrid = new JPanel(new GridLayout(2, 1, 0, 10));
        centerGrid.setOpaque(false);

        // TOP CARD: 2-D SEGMENT INVENTORY MATRIX
        CardPanel matrixCard = new CardPanel(new BorderLayout(0, 6));
        matrixCard.setBorder(new EmptyBorder(10, 14, 10, 14));

        JPanel mHead = new JPanel(new BorderLayout());
        mHead.setOpaque(false);
        JLabel mTitle = new JLabel("2-D Segment Inventory Bitmap Matrix (Station Hops x Seat Capacity)");
        mTitle.setFont(F_HEADER);
        mTitle.setForeground(IRCTC_NAVY);

        dsaMetricsLbl = new JLabel();
        dsaMetricsLbl.setFont(F_SMALL);
        dsaMetricsLbl.setForeground(TEXT_MUTED);

        mHead.add(mTitle, BorderLayout.WEST);
        mHead.add(dsaMetricsLbl, BorderLayout.EAST);

        dsaMatrixGridContainer = new JPanel();
        dsaMatrixGridContainer.setOpaque(false);
        JScrollPane matrixScroll = new JScrollPane(dsaMatrixGridContainer);
        matrixScroll.setBorder(new LineBorder(BORDER_COLOR, 1, true));

        matrixCard.add(mHead, BorderLayout.NORTH);
        matrixCard.add(matrixScroll, BorderLayout.CENTER);

        // BOTTOM SPLIT: Confirmed Linked List vs FIFO Waitlist Queue
        JPanel bottomSplit = new JPanel(new GridLayout(1, 2, 10, 0));
        bottomSplit.setOpaque(false);

        CardPanel confCard = new CardPanel(new BorderLayout(0, 4));
        confCard.setBorder(new EmptyBorder(8, 10, 8, 10));
        JLabel confHdr = new JLabel("Confirmed Roster: Singly Linked List (Head -> Tail)");
        confHdr.setFont(F_BOLD);
        confHdr.setForeground(GREEN_OK);

        String[] rCols = {"PNR", "User", "From", "To", "Seat", "Fare (Rs.)"};
        dsaRosterTableModel = new DefaultTableModel(rCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable rTbl = createStyledTable(dsaRosterTableModel);
        confCard.add(confHdr, BorderLayout.NORTH);
        confCard.add(new JScrollPane(rTbl), BorderLayout.CENTER);

        CardPanel waitCard = new CardPanel(new BorderLayout(0, 4));
        waitCard.setBorder(new EmptyBorder(8, 10, 8, 10));
        JLabel waitHdr = new JLabel("Waiting List: FIFO Pointer Queue (Front -> Rear)");
        waitHdr.setFont(F_BOLD);
        waitHdr.setForeground(AMBER_WARN);

        String[] wCols = {"WL Pos", "PNR", "User", "From", "To", "Fare Held (Rs.)"};
        dsaWaitlistTableModel = new DefaultTableModel(wCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable wTbl = createStyledTable(dsaWaitlistTableModel);
        waitCard.add(waitHdr, BorderLayout.NORTH);
        waitCard.add(new JScrollPane(wTbl), BorderLayout.CENTER);

        bottomSplit.add(confCard);
        bottomSplit.add(waitCard);

        centerGrid.add(matrixCard);
        centerGrid.add(bottomSplit);

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(centerGrid, BorderLayout.CENTER);
        return panel;
    }

    private void populateDsaTrainCombo() {
        dsaTrainSelectCombo.removeAllItems();
        List<TrainInstance> list = engine.getAllUniqueTrains();
        for (TrainInstance ti : list) {
            dsaTrainSelectCombo.addItem(ti.trainId + " - " + ti.trainName + " (" + ti.date + ")");
        }
        updateDsaVisualizer();
    }

    private void updateDsaVisualizer() {
        dsaMatrixGridContainer.removeAll();
        dsaRosterTableModel.setRowCount(0);
        dsaWaitlistTableModel.setRowCount(0);

        int idx = dsaTrainSelectCombo.getSelectedIndex();
        List<TrainInstance> list = engine.getAllUniqueTrains();
        if (idx < 0 || idx >= list.size()) {
            dsaMatrixGridContainer.revalidate();
            dsaMatrixGridContainer.repaint();
            return;
        }

        TrainInstance ti = list.get(idx);

        dsaMetricsLbl.setText(String.format("Segments: %d | Total Seats: %d | Hash Buckets: 1009 | Route Key: %s",
                ti.numSegments, ti.seatCapacity, ti.stations[0] + "_" + ti.stations[ti.stations.length - 1] + "_" + ti.date));

        JPanel grid = new JPanel(new GridLayout(ti.numSegments + 1, ti.seatCapacity + 1, 4, 4));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(8, 8, 8, 8));

        grid.add(new JLabel("<html><b>Segment / Seat -></b></html>"));

        for (int seat = 0; seat < ti.seatCapacity; seat++) {
            JLabel sl = new JLabel("Seat #" + seat, SwingConstants.CENTER);
            sl.setFont(F_BOLD);
            sl.setForeground(IRCTC_NAVY);
            grid.add(sl);
        }

        for (int seg = 0; seg < ti.numSegments; seg++) {
            String segLabel = ti.stations[seg] + " -> " + ti.stations[seg + 1];
            JLabel rl = new JLabel(segLabel);
            rl.setFont(F_BOLD);
            rl.setForeground(TEXT_DARK);
            grid.add(rl);

            for (int seat = 0; seat < ti.seatCapacity; seat++) {
                boolean booked = ti.segmentInventory[seg][seat];
                JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
                JLabel cl = new JLabel(booked ? "OCCUPIED" : "AVAILABLE");
                cl.setFont(F_SMALL);

                if (booked) {
                    cell.setBackground(RED_BG);
                    cell.setBorder(new LineBorder(new Color(252, 165, 165), 1, true));
                    cl.setForeground(RED_ERR);
                } else {
                    cell.setBackground(GREEN_BG);
                    cell.setBorder(new LineBorder(new Color(187, 247, 208), 1, true));
                    cl.setForeground(GREEN_OK);
                }
                cell.add(cl);
                grid.add(cell);
            }
        }

        dsaMatrixGridContainer.setLayout(new BorderLayout());
        dsaMatrixGridContainer.add(grid, BorderLayout.NORTH);

        List<PassengerNode> conf = ti.confirmedSnapshot();
        for (PassengerNode p : conf) {
            dsaRosterTableModel.addRow(new Object[]{
                p.pnr, p.userId, p.src, p.dest, p.assignedSeat, String.format("%.2f", p.farePaid)
            });
        }

        List<PassengerNode> wait = ti.waitingList.snapshot();
        int pos = 1;
        for (PassengerNode w : wait) {
            dsaWaitlistTableModel.addRow(new Object[]{
                "WL " + (pos++), w.pnr, w.userId, w.src, w.dest, String.format("%.2f", w.farePaid)
            });
        }

        dsaMatrixGridContainer.revalidate();
        dsaMatrixGridContainer.repaint();
    }

    // =========================================================================
    // CONSTRUCTOR & APPLICATION ENTRY POINT
    // =========================================================================
    public ModernRailwayApp() {
        super("IRCTC NextGen Passenger Reservation System (Ultra-Low-Latency DSA Core)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1050, 700));
        setLocationRelativeTo(null);

        javax.swing.Timer clockTimer = new javax.swing.Timer(1000, e -> updateClock());
        clockTimer.start();

        rootContainer.add(buildLoginScreen(), "LOGIN");
        rootContainer.add(buildMainPortal(), "MAIN_APP");

        add(rootContainer);
        cardLayout.show(rootContainer, "LOGIN");
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        UIManager.put("Button.font", F_BOLD);
        UIManager.put("Label.font", F_BODY);
        UIManager.put("ComboBox.font", F_BODY);
        UIManager.put("TextField.font", F_BODY);

        SwingUtilities.invokeLater(() -> {
            ModernRailwayApp app = new ModernRailwayApp();
            app.setVisible(true);
        });
    }
}
