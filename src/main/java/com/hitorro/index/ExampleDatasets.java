/*
 * Copyright (c) 2006-2025 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.index;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hitorro.index.config.IndexConfig;
import com.hitorro.jsontypesystem.JVS;

import java.io.IOException;
import java.util.*;

/**
 * Pre-built example datasets using proper JVS type system conventions.
 * <p>
 * Each document follows the type system: {@code type} field references a type definition
 * from {@code config/types/}, {@code id} uses {@code domain/did} structure, text fields
 * use MLS (Multi-Language String) format with {@code {mls: [{lang, text}]}} arrays,
 * and vector fields use arrays.
 * <p>
 * Provides three distinct datasets mapping to demo types:
 * <ul>
 *     <li><b>articles</b> - {@code demo_article} type: title, body, content (MLS), author, category, tags</li>
 *     <li><b>products</b> - {@code demo_product} type: title, description (MLS), brand, price, category, sku</li>
 *     <li><b>documents</b> - {@code demo_document} type: title, body (MLS), author, department, keywords, classification</li>
 * </ul>
 */
public final class ExampleDatasets {

    private ExampleDatasets() {}

    public enum Dataset {
        ARTICLES("articles"),
        PRODUCTS("products"),
        DOCUMENTS("documents");

        private final String indexName;

        Dataset(String indexName) {
            this.indexName = indexName;
        }

        public String getIndexName() {
            return indexName;
        }
    }

    public static class LoadResult {
        private final IndexManager manager;
        private final Map<Dataset, Integer> loadedCounts;

        LoadResult(IndexManager manager, Map<Dataset, Integer> loadedCounts) {
            this.manager = manager;
            this.loadedCounts = Collections.unmodifiableMap(loadedCounts);
        }

        public IndexManager getManager() { return manager; }
        public Map<Dataset, Integer> getLoadedCounts() { return loadedCounts; }
        public int getTotalLoaded() { return loadedCounts.values().stream().mapToInt(Integer::intValue).sum(); }
        public Set<Dataset> getLoadedDatasets() { return loadedCounts.keySet(); }
    }

    // ========== Load methods ==========

    public static LoadResult load(IndexManager manager, Dataset... datasets) throws IOException {
        Map<Dataset, Integer> counts = new LinkedHashMap<>();
        for (Dataset ds : datasets) {
            List<JVS> docs = getDocuments(ds);
            IndexConfig config = IndexConfig.inMemory().build();
            manager.createIndex(ds.getIndexName(), config, null);
            manager.indexDocuments(ds.getIndexName(), docs);
            manager.commit(ds.getIndexName());
            counts.put(ds, docs.size());
        }
        return new LoadResult(manager, counts);
    }

    public static LoadResult loadAll(IndexManager manager) throws IOException {
        return load(manager, Dataset.values());
    }

    public static LoadResult loadArticles(IndexManager manager) throws IOException {
        return load(manager, Dataset.ARTICLES);
    }

    public static LoadResult loadProducts(IndexManager manager) throws IOException {
        return load(manager, Dataset.PRODUCTS);
    }

    public static LoadResult loadDocuments(IndexManager manager) throws IOException {
        return load(manager, Dataset.DOCUMENTS);
    }

    // ========== Raw document accessors ==========

    public static List<JVS> getDocuments(Dataset dataset) {
        return switch (dataset) {
            case ARTICLES -> getArticles();
            case PRODUCTS -> getProducts();
            case DOCUMENTS -> getDocumentDocs();
        };
    }

    public static List<Dataset> getAvailableDatasets() {
        return List.of(Dataset.values());
    }

    // ========== Articles dataset (15 documents, type: demo_article) ==========

    public static List<JVS> getArticles() {
        List<JVS> docs = new ArrayList<>();
        docs.add(article("art-001", "Climate Change Impact on Agriculture",
                "Rising global temperatures are significantly affecting crop yields worldwide. Researchers have found that wheat production could decline by 6% for each degree Celsius of warming.",
                "Dr. Sarah Chen", "Environmental Science", "Nature Reviews", new String[]{"climate", "agriculture", "research"}));
        docs.add(article("art-002", "Advances in Quantum Computing",
                "A new quantum processor has achieved quantum supremacy by solving a problem that would take classical computers thousands of years. The 127-qubit chip uses superconducting circuits.",
                "Prof. James Miller", "Computer Science", "Science", new String[]{"quantum", "computing", "technology"}));
        docs.add(article("art-003", "The Future of Remote Work",
                "Post-pandemic surveys show that 67% of knowledge workers prefer hybrid arrangements. Companies adopting flexible policies report higher retention and productivity metrics.",
                "Maria Rodriguez", "Business", "Harvard Business Review", new String[]{"remote work", "workplace", "management"}));
        docs.add(article("art-004", "Deep Ocean Ecosystems Discovery",
                "Marine biologists have discovered three new species of bioluminescent organisms in the Mariana Trench at depths exceeding 8000 meters.",
                "Dr. Kenji Tanaka", "Marine Biology", "Deep-Sea Research", new String[]{"ocean", "biology", "discovery"}));
        docs.add(article("art-005", "Artificial Intelligence in Healthcare",
                "Machine learning algorithms are now detecting early-stage cancer with 94% accuracy, outperforming radiologists in some diagnostic tasks. The models were trained on millions of medical images.",
                "Dr. Emily Watson", "Medical Research", "The Lancet", new String[]{"AI", "healthcare", "machine learning"}));
        docs.add(article("art-006", "Renewable Energy Grid Integration",
                "New battery storage solutions are enabling grid-scale integration of solar and wind power. Lithium iron phosphate batteries are achieving 95% round-trip efficiency.",
                "Robert Park", "Energy", "Energy Policy", new String[]{"renewable", "solar", "battery", "energy"}));
        docs.add(article("art-007", "CRISPR Gene Therapy Breakthrough",
                "The first FDA-approved CRISPR therapy for sickle cell disease has shown complete remission in 29 of 31 patients during phase 3 clinical trials.",
                "Dr. Lisa Zhang", "Genetics", "New England Journal of Medicine", new String[]{"CRISPR", "gene therapy", "medicine"}));
        docs.add(article("art-008", "Urban Planning for Climate Resilience",
                "Cities are redesigning infrastructure to withstand extreme weather events. Green corridors, permeable surfaces, and urban forests are key strategies being adopted globally.",
                "Anna Petrov", "Urban Studies", "Urban Planning Quarterly", new String[]{"urban planning", "climate", "infrastructure"}));
        docs.add(article("art-009", "The Microbiome and Mental Health",
                "Gut bacteria have been found to produce neurotransmitters that affect mood and cognition. Studies link microbiome diversity to reduced rates of depression and anxiety.",
                "Dr. Michael Torres", "Neuroscience", "Nature Neuroscience", new String[]{"microbiome", "mental health", "neuroscience"}));
        docs.add(article("art-010", "Space Mining Economics",
                "Asteroid mining could become economically viable by 2040. A single platinum-rich asteroid could contain metals worth trillions of dollars at current market prices.",
                "Prof. Diana Novak", "Aerospace", "Space Policy", new String[]{"space", "mining", "economics"}));
        docs.add(article("art-011", "Blockchain in Supply Chain Management",
                "Major retailers are using distributed ledger technology to track products from farm to shelf. Transparency has reduced food fraud incidents by 40% in pilot programs.",
                "David Kim", "Business", "MIT Technology Review", new String[]{"blockchain", "supply chain", "technology"}));
        docs.add(article("art-012", "Neuroplasticity and Language Learning",
                "Brain imaging studies reveal that adult language learners develop new neural pathways similar to children. Immersive VR environments accelerate acquisition by 30%.",
                "Dr. Sophie Martin", "Cognitive Science", "Cognition", new String[]{"neuroplasticity", "language", "learning"}));
        docs.add(article("art-013", "Sustainable Architecture Materials",
                "Cross-laminated timber is emerging as a carbon-negative alternative to steel and concrete. An 18-story timber building in Norway demonstrates structural viability.",
                "Henrik Larsen", "Architecture", "Architectural Record", new String[]{"architecture", "sustainability", "materials"}));
        docs.add(article("art-014", "Autonomous Vehicle Safety Standards",
                "New ISO standards for self-driving cars require vehicles to handle 37 critical scenario types. Level 4 autonomy certification now mandates 10 million miles of testing data.",
                "Jennifer Walsh", "Transportation", "IEEE Spectrum", new String[]{"autonomous vehicles", "safety", "standards"}));
        docs.add(article("art-015", "Dark Matter Detection Advances",
                "The latest xenon-based detector has achieved unprecedented sensitivity, ruling out several theoretical dark matter candidates and narrowing the search range significantly.",
                "Prof. Alexei Volkov", "Physics", "Physical Review Letters", new String[]{"dark matter", "physics", "detection"}));
        return docs;
    }

    // ========== Products dataset (15 documents, type: demo_product) ==========

    public static List<JVS> getProducts() {
        List<JVS> docs = new ArrayList<>();
        docs.add(product("prod-001", "Ultra-Slim Laptop Pro 15",
                "15.6 inch laptop with M3 chip, 16GB RAM, 512GB SSD. Retina display with 120Hz refresh rate. All-day battery life up to 18 hours.",
                "TechWave", 1299, "Electronics", true, "TW-LAPTOP-15"));
        docs.add(product("prod-002", "Wireless Noise-Canceling Headphones",
                "Premium over-ear headphones with adaptive noise cancellation. 40-hour battery, multipoint Bluetooth, and spatial audio support.",
                "SoundCore", 249, "Electronics", true, "SC-WNC-400"));
        docs.add(product("prod-003", "Organic French Press Coffee Beans",
                "Single-origin Ethiopian Yirgacheffe beans, medium roast. Notes of blueberry, dark chocolate, and citrus. Fair trade certified, 1kg bag.",
                "Bean Republic", 24, "Food & Beverage", true, "BR-COFFEE-ETH"));
        docs.add(product("prod-004", "Ergonomic Standing Desk",
                "Electric height-adjustable desk with memory presets. Bamboo top surface, cable management tray, 70x30 inches. Supports up to 300 lbs.",
                "DeskCraft", 549, "Furniture", true, "DC-DESK-70"));
        docs.add(product("prod-005", "Trail Running Shoes GTX",
                "Waterproof trail runners with GORE-TEX lining. Vibram Megagrip outsole, rock plate protection, and responsive foam cushioning.",
                "TrailBlazer", 179, "Sports & Outdoors", true, "TB-TRAIL-GTX"));
        docs.add(product("prod-006", "Smart Home Security Camera",
                "4K indoor/outdoor camera with AI person detection. Night vision, two-way audio, local and cloud storage. Works with Alexa and Google Home.",
                "SecureView", 89, "Smart Home", true, "SV-CAM-4K"));
        docs.add(product("prod-007", "Professional Chef Knife Set",
                "Japanese VG-10 stainless steel, 8-piece set with magnetic walnut block. Includes chef, santoku, bread, paring, and utility knives.",
                "EdgeMaster", 329, "Kitchen", true, "EM-KNIFE-8"));
        docs.add(product("prod-008", "Yoga Mat Premium",
                "6mm thick natural rubber mat with alignment lines. Non-slip surface on both sides, antimicrobial treatment, includes carry strap.",
                "ZenFit", 68, "Sports & Outdoors", true, "ZF-MAT-6MM"));
        docs.add(product("prod-009", "Portable Bluetooth Speaker",
                "Waterproof IPX7 speaker with 360-degree sound. 24-hour battery, built-in microphone, and daisy-chain pairing for multiple speakers.",
                "SoundCore", 79, "Electronics", true, "SC-SPK-360"));
        docs.add(product("prod-010", "Electric Toothbrush Smart",
                "Sonic toothbrush with pressure sensor and app connectivity. 5 brushing modes, 2-week battery life, includes 3 brush heads and travel case.",
                "DentalPro", 129, "Health & Personal Care", true, "DP-BRUSH-S5"));
        docs.add(product("prod-011", "Mechanical Keyboard RGB",
                "Hot-swappable mechanical keyboard with Cherry MX switches. Per-key RGB lighting, aluminum frame, PBT keycaps, USB-C detachable cable.",
                "KeyForge", 159, "Electronics", true, "KF-KB-RGB"));
        docs.add(product("prod-012", "Insulated Water Bottle",
                "Triple-wall vacuum insulation keeps drinks cold 36 hours or hot 18 hours. 32oz, stainless steel, powder-coated finish, leak-proof lid.",
                "HydraFlask", 34, "Sports & Outdoors", true, "HF-BOTTLE-32"));
        docs.add(product("prod-013", "Wireless Charging Pad",
                "15W fast wireless charger compatible with Qi devices. Slim profile with LED indicator, includes USB-C cable. Foreign object detection.",
                "TechWave", 29, "Electronics", false, "TW-CHARGE-15W"));
        docs.add(product("prod-014", "Cast Iron Dutch Oven",
                "6-quart enameled cast iron dutch oven. Self-basting lid, oven-safe to 500F. Ideal for braising, roasting, and baking sourdough bread.",
                "IronChef", 89, "Kitchen", true, "IC-DUTCH-6QT"));
        docs.add(product("prod-015", "Noise Machine for Sleep",
                "White noise machine with 30 sound options including rain, ocean, and fan. Adjustable volume and timer, compact travel-friendly design.",
                "RestWell", 44, "Health & Personal Care", true, "RW-NOISE-30"));
        return docs;
    }

    // ========== Documents dataset (15 documents, type: demo_document) ==========

    public static List<JVS> getDocumentDocs() {
        List<JVS> docs = new ArrayList<>();
        docs.add(document("doc-001", "legal",
                "Merger Agreement Between Acme Corporation and GlobalTech Industries",
                "This merger agreement is entered into by Acme Corporation, headquartered in San Francisco, and GlobalTech Industries, based in London. The transaction valued at approximately $4.7 billion was negotiated by lead counsel Sarah Mitchell.",
                "Sarah Mitchell", "Legal", new String[]{"merger", "acquisition", "regulatory"}, "confidential"));
        docs.add(document("doc-002", "research",
                "Annual Climate Report for the United Nations Environment Programme",
                "Global temperatures have risen 1.2 degrees Celsius above pre-industrial levels. The Arctic ice sheet has lost 13% of its area per decade since satellite measurements began. Dr. Maria Santos and the IPCC Working Group II prepared this report.",
                "Dr. Maria Santos", "Research", new String[]{"climate", "environment", "sustainability"}, "public"));
        docs.add(document("doc-003", "finance",
                "Q3 Earnings Report and Market Analysis by Morgan Stanley",
                "Third quarter revenue reached $14.2 billion, representing a 12% year-over-year increase. Net income rose to $2.8 billion driven by strong performance in institutional securities.",
                "Tim Cook", "Finance", new String[]{"earnings", "quarterly", "analysis"}, "confidential"));
        docs.add(document("doc-004", "engineering",
                "Technical Specification for Next-Generation Cloud Infrastructure",
                "The proposed microservices architecture supports horizontal scaling to 10,000 concurrent nodes. Service mesh implementation using Istio provides automatic load balancing and circuit breaking.",
                "David Chen", "Engineering", new String[]{"cloud", "architecture", "microservices"}, "internal"));
        docs.add(document("doc-005", "hr",
                "Employee Handbook and Corporate Policy Guidelines",
                "All employees are entitled to 25 days of annual leave plus national holidays. Remote work policy allows up to 3 days per week with manager approval.",
                "Jennifer Walsh", "Human Resources", new String[]{"policy", "handbook", "hr"}, "internal"));
        docs.add(document("doc-006", "research",
                "Mars Exploration Program Status Report from NASA JPL",
                "The Perseverance rover has collected 24 rock and soil samples from Jezero Crater. Dr. James Hansen at the Jet Propulsion Laboratory confirms evidence of ancient microbial activity.",
                "Dr. James Hansen", "Research", new String[]{"space", "mars", "exploration"}, "public"));
        docs.add(document("doc-007", "legal",
                "Patent Filing for Autonomous Navigation System",
                "Novel approach to real-time path planning using transformer-based neural networks. The system achieves sub-millisecond response times for obstacle avoidance in urban environments.",
                "Robert Park", "Legal", new String[]{"patent", "autonomous", "navigation"}, "confidential"));
        docs.add(document("doc-008", "marketing",
                "Global Brand Strategy and Market Positioning Report",
                "Consumer surveys across 15 markets reveal strong brand recognition in the 25-44 demographic. Social media engagement has increased 340% following the influencer partnership program.",
                "Anna Petrov", "Marketing", new String[]{"brand", "strategy", "marketing"}, "internal"));
        docs.add(document("doc-009", "engineering",
                "Database Migration Plan from Oracle to PostgreSQL",
                "Phased migration of 47 schemas containing 2.3 billion rows. Zero-downtime approach using logical replication and dual-write proxies during transition period.",
                "Kenji Tanaka", "Engineering", new String[]{"database", "migration", "postgresql"}, "internal"));
        docs.add(document("doc-010", "research",
                "Breakthrough in Solid-State Battery Technology",
                "New ceramic electrolyte achieves ionic conductivity of 10 mS/cm at room temperature, matching liquid electrolytes. Energy density reaches 500 Wh/kg with 1000 cycle stability.",
                "Prof. Diana Novak", "Research", new String[]{"battery", "energy", "materials"}, "public"));
        docs.add(document("doc-011", "finance",
                "Risk Assessment for Emerging Market Portfolio",
                "Currency volatility in Southeast Asian markets has increased 23% year-over-year. Hedging strategies using cross-currency swaps are recommended for positions exceeding $50 million.",
                "Michael Torres", "Finance", new String[]{"risk", "markets", "portfolio"}, "confidential"));
        docs.add(document("doc-012", "legal",
                "Data Privacy Compliance Audit Report for GDPR",
                "Audit of 14 data processing systems revealed 3 high-priority findings related to data retention policies. Right-to-deletion requests are processed within 72 hours on average.",
                "Lisa Zhang", "Legal", new String[]{"privacy", "GDPR", "compliance"}, "confidential"));
        docs.add(document("doc-013", "engineering",
                "API Gateway Performance Benchmarks and Optimization Guide",
                "Load testing at 50,000 requests per second shows p99 latency of 12ms. Rate limiting and JWT validation add 2ms overhead. Connection pooling reduced upstream latency by 40%.",
                "David Kim", "Engineering", new String[]{"API", "performance", "optimization"}, "internal"));
        docs.add(document("doc-014", "research",
                "Coral Reef Restoration Progress in the Great Barrier Reef",
                "Selective breeding program has produced heat-resistant coral variants surviving water temperatures 2 degrees above historical averages. 15,000 coral fragments transplanted this season.",
                "Dr. Sophie Martin", "Research", new String[]{"coral", "marine", "restoration"}, "public"));
        docs.add(document("doc-015", "hr",
                "Diversity and Inclusion Annual Report",
                "Gender parity reached at director level for the first time. Neurodiversity hiring program placed 45 candidates across engineering and design teams. Employee satisfaction up 8 points.",
                "Antonio Guterres", "Human Resources", new String[]{"diversity", "inclusion", "report"}, "public"));
        return docs;
    }

    // ========== Document builders (JVS type system format) ==========

    private static ArrayNode toArray(String... values) {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        for (String v : values) arr.add(v);
        return arr;
    }

    private static JVS article(String did, String title, String content,
                               String author, String category, String publication, String[] tags) {
        JVS doc = new JVS();
        doc.set("type", "demo_article");
        doc.set("id.domain", "articles");
        doc.set("id.did", did);
        doc.set("title.mls[0].lang", "en");
        doc.set("title.mls[0].text", title);
        doc.set("content.mls[0].lang", "en");
        doc.set("content.mls[0].text", content);
        doc.set("author", author);
        doc.set("category", toArray(category));
        doc.set("tags", toArray(tags));
        doc.set("publication", publication);
        return doc;
    }

    private static JVS product(String did, String name, String description,
                                String brand, long price, String category, boolean inStock, String sku) {
        JVS doc = new JVS();
        doc.set("type", "demo_product");
        doc.set("id.domain", "products");
        doc.set("id.did", did);
        doc.set("title.mls[0].lang", "en");
        doc.set("title.mls[0].text", name);
        doc.set("description.mls[0].lang", "en");
        doc.set("description.mls[0].text", description);
        doc.set("brand", brand);
        doc.set("price", price);
        doc.set("category", toArray(category));
        doc.set("in_stock", inStock);
        doc.set("sku", sku);
        doc.set("currency", "USD");
        return doc;
    }

    private static JVS document(String did, String domain, String title, String body,
                                 String author, String department, String[] keywords, String classification) {
        JVS doc = new JVS();
        doc.set("type", "demo_document");
        doc.set("id.domain", domain);
        doc.set("id.did", did);
        doc.set("title.mls[0].lang", "en");
        doc.set("title.mls[0].text", title);
        doc.set("body.mls[0].lang", "en");
        doc.set("body.mls[0].text", body);
        doc.set("author", author);
        doc.set("department", department);
        doc.set("keywords", toArray(keywords));
        doc.set("classification", classification);
        return doc;
    }
}
