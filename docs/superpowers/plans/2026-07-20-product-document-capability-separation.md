# Product Document and Capability Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将产品文档树从产品模块/功能树中彻底拆分，并把消保合规产品迁移为 11 个文档目录与 10 个业务模块、31 个子模块、124 个原子功能。

**Architecture:** 新增 `product_document_node` 作为产品文档元数据与 Outline 链接的唯一关系表，复用现有 `DocumentCenterService` 读写 Outline。`product_module` 与 `product_feature` 只保存产品能力；现有产品保存流程不再调用 Outline。Flyway 迁移先复制并校验全部 Outline 关系，再重建消保能力树，最后删除误建的文档型模块与功能。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、Flyway、MySQL 8/H2、React 18、TypeScript、Ant Design、TanStack Query、Vitest。

## Global Constraints

- 保留现有 Outline 文档 ID、正文和修订，不调用 Outline 删除接口。
- 文档树最多四级；模块树继续最多三级。
- `product_document_node(product_id, code)`、`outline_link_id`、非空 `linked_feature_id` 分别唯一。
- 模块或功能保存不得创建、移动、读取或改名 Outline 文档。
- 消保合规迁移重复执行不得生成重复节点、模块或功能。
- 不修改当前工作区内 `OpenAiCompatibleClient.java`、`OpenAiCompatibleClientTest.java` 和两份未跟踪调研报告。

---

### Task 1: 独立产品文档节点表

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__product_document_nodes.sql`
- Modify: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`

**Interfaces:**
- Consumes: `product(id)`、`product_feature(id)`、`outline_document_link(id)`。
- Produces: `product_document_node(id, product_id, parent_id, node_type, code, title, description, sort_order, outline_link_id, linked_feature_id, created_at, updated_at, version)`。

- [ ] **Step 1: 写失败的模式测试**

在 `SchemaBaselineTest` 增加：

```java
@Test
void flywayCreatesIndependentProductDocumentNodes() {
  assertEquals(Integer.valueOf(1), jdbc.queryForObject(
      "select count(*) from information_schema.tables where table_schema='public' "
          + "and table_name='product_document_node'", Integer.class));
  assertEquals(Integer.valueOf(3), jdbc.queryForObject(
      "select count(*) from information_schema.table_constraints "
          + "where table_schema='public' and table_name='product_document_node' "
          + "and constraint_name in ('uk_product_document_code',"
          + "'uk_product_document_outline','uk_product_document_feature')", Integer.class));
}
```

- [ ] **Step 2: 验证测试因缺表失败**

Run: `cd backend && mvn -q -Dtest=SchemaBaselineTest#flywayCreatesIndependentProductDocumentNodes test`

Expected: FAIL，`product_document_node` 数量为 `0`。

- [ ] **Step 3: 添加最小表结构**

`V19__product_document_nodes.sql` 创建字段及以下约束：

```sql
CREATE TABLE product_document_node (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  node_type VARCHAR(16) NOT NULL,
  code VARCHAR(96) NOT NULL,
  title VARCHAR(240) NOT NULL,
  description VARCHAR(1000) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  outline_link_id BIGINT NULL,
  linked_feature_id BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_product_document_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_product_document_parent FOREIGN KEY (parent_id) REFERENCES product_document_node(id),
  CONSTRAINT fk_product_document_outline FOREIGN KEY (outline_link_id) REFERENCES outline_document_link(id),
  CONSTRAINT fk_product_document_feature FOREIGN KEY (linked_feature_id) REFERENCES product_feature(id),
  CONSTRAINT uk_product_document_code UNIQUE (product_id,code),
  CONSTRAINT uk_product_document_outline UNIQUE (outline_link_id),
  CONSTRAINT uk_product_document_feature UNIQUE (linked_feature_id)
);
CREATE INDEX idx_product_document_tree
  ON product_document_node(product_id,parent_id,sort_order,id);
```

- [ ] **Step 4: 验证模式测试通过**

Run: `cd backend && mvn -q -Dtest=SchemaBaselineTest#flywayCreatesIndependentProductDocumentNodes test`

Expected: PASS，1 test，0 failures。

- [ ] **Step 5: 提交表结构**

```bash
git add backend/src/main/resources/db/migration/V19__product_document_nodes.sql backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java
git commit -m "feat: add independent product document nodes"
```

### Task 2: 独立文档节点 API 与 Outline 内容读写

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/catalog/ProductDocumentNodeService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductDocumentController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/catalog/ProductDocumentNodeApiIT.java`

**Interfaces:**
- Consumes: `DocumentCenterService.ensureIndex`、`createDocument`、`readLink`、`updateLink`、`moveBusinessDocument`。
- Produces: `nodes(long organizationId,long productId)`、`saveNode(long organizationId,long actorId,long productId,Long id,Long parentId,String nodeType,String code,String title,String description,int sortOrder,Long linkedFeatureId,long version)`、`retry(...)`、`readContent(...)`、`saveContent(...)`。
- Produces HTTP: `GET/POST /document-nodes`、`PUT /document-nodes/{nodeId}`、`POST /document-nodes/{nodeId}/retry`、`GET/PUT /document-nodes/{nodeId}/content`、`GET /document-nodes/{nodeId}/export`。

- [ ] **Step 1: 写失败的 API 测试**

`ProductDocumentNodeApiIT` 使用 H2、MockMvc 和内存 `OutlineClient`，覆盖以下真实行为：

```java
mvc.perform(get("/api/v1/products/{productId}/document-nodes", 3300).with(reader()))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$[0].code").value("DOC-01"))
    .andExpect(jsonPath("$[1].parentId").value(3401));

mvc.perform(post("/api/v1/products/{productId}/document-nodes", 3300)
        .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"nodeType\":\"DOCUMENT\",\"parentId\":3401,"
            + "\"code\":\"DOC-01-01\",\"title\":\"产品一页纸\",\"sortOrder\":1}"))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.syncStatus").value("READY"));
```

同一测试类再验证：跨产品父节点返回 400、第五级返回 400、移动成环返回 400、重复编码返回 409、文档内容 GET/PUT 使用 Outline revision、文件夹内容返回 400、未关联 Spec 返回 404。

- [ ] **Step 2: 验证 API 测试因路由不存在失败**

Run: `cd backend && mvn -q -Dtest=ProductDocumentNodeApiIT test`

Expected: FAIL，首个 `/document-nodes` 请求返回 404。

- [ ] **Step 3: 实现最小文档节点服务**

服务只使用 `JdbcTemplate` 与现有 `DocumentCenterService`：

```java
public List<Map<String, Object>> nodes(long organizationId, long productId) {
  product(organizationId, productId);
  return jdbc.query("select n.id,n.product_id,n.parent_id,n.node_type,n.code,n.title,"
          + "n.description,n.sort_order,n.outline_link_id,n.linked_feature_id,n.version,"
          + "coalesce(l.sync_status,'PENDING') sync_status "
          + "from product_document_node n left join outline_document_link l "
          + "on l.id=n.outline_link_id where n.product_id=? "
          + "order by case when n.parent_id is null then 0 else 1 end,n.sort_order,n.id",
      (row, index) -> nodeRow(row), productId);
}
```

`saveNode` 先校验同产品父节点、四级限制、成环、`nodeType` 和可选功能归属，再插入或用 `version` 更新。随后调用 `syncNode`：根节点父级使用 `PRODUCT:{productId}` 索引；`FOLDER` 调用 `ensureIndex`；`DOCUMENT` 调用 `createDocument`；创建或失败后均用 `findLinkId` 回填 `outline_link_id`。更新节点仅在显式重试时同步标题与 Outline 位置，避免数据库保存失败导致正文丢失。

内容接口直接按节点 `outline_link_id` 调用：

```java
public DocumentView readContent(long organizationId, long productId, long nodeId) {
  Map<String, Object> node = documentNode(organizationId, productId, nodeId);
  requireDocument(node);
  return documents.readLink(((Number) node.get("outline_link_id")).longValue(), organizationId);
}
```

- [ ] **Step 4: 改造控制器到独立路由**

将 `ProductDocumentController` 注入类型改为 `ProductDocumentNodeService`，新增 `NodeRequest`：

```java
public static final class NodeRequest {
  public Long parentId;
  @NotBlank public String nodeType;
  @NotBlank public String code;
  @NotBlank public String title;
  public String description;
  public int sortOrder;
  public Long linkedFeatureId;
  public long version;
}
```

保留 `/features/{featureId}/spec` 兼容路由，但通过 `linked_feature_id` 找文档节点；没有显式关联时返回 404，不创建 Outline 文档。

- [ ] **Step 5: 验证节点 API 测试通过**

Run: `cd backend && mvn -q -Dtest=ProductDocumentNodeApiIT test`

Expected: PASS，全部节点、内容及校验用例为 0 failures。

- [ ] **Step 6: 提交节点 API**

```bash
git add backend/src/main/java/com/zhilu/delivery/catalog/ProductDocumentNodeService.java backend/src/main/java/com/zhilu/delivery/catalog/ProductDocumentController.java backend/src/test/java/com/zhilu/delivery/catalog/ProductDocumentNodeApiIT.java
git commit -m "feat: expose independent product document workspace"
```

### Task 3: 产品能力保存与 Outline 解耦

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureController.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/catalog/ProductDocumentAutoSyncTest.java`
- Delete: `backend/src/main/java/com/zhilu/delivery/catalog/ProductDocumentService.java`
- Delete: `backend/src/test/java/com/zhilu/delivery/catalog/ProductDocumentServiceTest.java`
- Delete: `backend/src/test/java/com/zhilu/delivery/catalog/ProductDocumentApiIT.java`

**Interfaces:**
- Consumes: `ProductCatalogService`、`ProductStructureService`。
- Produces: 产品、模块、功能保存只修改业务表；文档服务只能由文档控制器调用。

- [ ] **Step 1: 将自动同步测试反转为失败测试**

在 `ProductDocumentAutoSyncTest` 用 `@MockBean ProductDocumentNodeService documents`，创建/更新产品、模块、功能后断言：

```java
verifyNoInteractions(documents);
```

- [ ] **Step 2: 验证测试仍观察到自动同步**

Run: `cd backend && mvn -q -Dtest=ProductDocumentAutoSyncTest test`

Expected: FAIL，报告产品、模块或功能保存调用了文档同步。

- [ ] **Step 3: 删除控制器自动同步依赖**

从两个控制器构造器移除文档服务，并删除 `syncProductDocuments`、`syncModuleDocuments`、`syncFeatureDocuments` 及所有调用。删除旧 `ProductDocumentService` 和只验证旧耦合行为的测试。

- [ ] **Step 4: 验证能力保存不触碰 Outline**

Run: `cd backend && mvn -q -Dtest=ProductDocumentAutoSyncTest,ProductStructureIT,ProductCatalogIT test`

Expected: PASS，0 failures。

- [ ] **Step 5: 提交解耦改动**

```bash
git add -A backend/src/main/java/com/zhilu/delivery/catalog backend/src/test/java/com/zhilu/delivery/catalog
git commit -m "refactor: decouple product capabilities from outline"
```

### Task 4: 前端独立文档工作区

**Files:**
- Modify: `frontend/src/modules/product/types.ts`
- Modify: `frontend/src/modules/product/productApi.ts`
- Modify: `frontend/src/modules/product/ProductDocumentsTab.tsx`
- Modify: `frontend/src/modules/product/ProductDetailPage.test.tsx`

**Interfaces:**
- Consumes: Task 2 的 `/document-nodes` 与 `/document-nodes/{id}/content` API。
- Produces: `ProductDocumentNode` 使用 `nodeType: 'FOLDER' | 'DOCUMENT'`、`code`、`linkedFeatureId`；产品文档页不引用模块或功能层级。

- [ ] **Step 1: 写失败的前端独立树测试**

将 fixture 改为：

```ts
const productDocuments = [
  { id: 101, productId: 8, nodeType: 'FOLDER', code: 'DOC-01', title: '01 产品总纲', sortOrder: 1, syncStatus: 'READY', version: 0 },
  { id: 102, productId: 8, parentId: 101, nodeType: 'DOCUMENT', code: 'DOC-01-01', title: '产品一页纸', sortOrder: 1, syncStatus: 'READY', version: 0 },
]
```

请求路径改为 `/api/v1/products/8/document-nodes` 和 `/document-nodes/102/content`，测试断言页面出现“独立文档工作区”，且不出现“产品结构与 Outline 实时对应”。

- [ ] **Step 2: 验证前端测试因旧路径和旧字段失败**

Run: `cd frontend && pnpm test:run src/modules/product/ProductDetailPage.test.tsx`

Expected: FAIL，请求仍访问 `/documents` 或树未显示 `nodeType` 数据。

- [ ] **Step 3: 切换类型和 API**

`types.ts` 定义：

```ts
export type ProductDocumentNodeType = 'FOLDER' | 'DOCUMENT'
export interface ProductDocumentNode {
  id: number
  productId: number
  parentId?: number
  nodeType: ProductDocumentNodeType
  code: string
  title: string
  description?: string
  sortOrder: number
  syncStatus: 'PENDING' | 'CREATING' | 'READY' | 'FAILED'
  linkedFeatureId?: number
  version: number
}
```

`productApi` 提供 `documentNodes`、`saveDocumentNode`、`retryDocumentNode`、`documentNodeContent`、`saveDocumentNodeContent`、`documentNodeExportUrl`，全部使用 Task 2 路由。

- [ ] **Step 4: 改造文档页**

树构建仅按 `parentId` 和 `nodeType`；选择 `DOCUMENT` 时加载 `DocumentWorkspace`；文件夹不可选正文。标题副文案改为“独立文档工作区 · 内容同步至 Outline”。写权限用户提供“新建文件夹”“新建文档”和失败节点“重试”按钮，表单提交 `parentId/nodeType/code/title/description/sortOrder/version`。

- [ ] **Step 5: 验证前端测试和构建通过**

Run: `cd frontend && pnpm test:run src/modules/product/ProductDetailPage.test.tsx`

Expected: PASS，0 failures。

Run: `cd frontend && pnpm build`

Expected: exit 0，TypeScript 与 Vite 构建成功。

- [ ] **Step 6: 提交前端改造**

```bash
git add frontend/src/modules/product/types.ts frontend/src/modules/product/productApi.ts frontend/src/modules/product/ProductDocumentsTab.tsx frontend/src/modules/product/ProductDetailPage.test.tsx
git commit -m "feat: show independent product document tree"
```

### Task 5: 消保合规数据迁移与上线验收

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__consumer_protection_product_structure.sql`
- Modify: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`
- Modify: `docs/superpowers/plans/2026-07-20-product-document-capability-separation.md`

**Interfaces:**
- Consumes: 消保合规产品编码 `XBHG`；旧编码 `DOC-%`、`SPRINT-%`、`CAP-%`；现有 `outline_link_id`。
- Produces: 26 个迁移文件夹节点、70 个迁移文档节点、10 个一级能力模块、31 个二级模块、124 个原子功能；旧 Outline 链接保持原 ID。

- [ ] **Step 1: 写失败的迁移验收测试**

在独立 H2 数据源先迁移到 V19，插入 `XBHG` 产品、26 个旧模块、70 个旧功能及对应 Outline 链接，再迁移到最新版本，断言：

```java
assertEquals(Integer.valueOf(11), legacy.queryForObject(
    "select count(*) from product_document_node where product_id=102 and parent_id is null",
    Integer.class));
assertEquals(Integer.valueOf(39), legacy.queryForObject(
    "select count(*) from product_document_node where product_id=102 "
        + "and node_type='DOCUMENT' and (code like 'DOC-%' or code like 'SPRINT-%')",
    Integer.class));
assertEquals(Integer.valueOf(10), legacy.queryForObject(
    "select count(*) from product_module where product_id=102 and parent_id is null",
    Integer.class));
assertEquals(Integer.valueOf(31), legacy.queryForObject(
    "select count(*) from product_module where product_id=102 and parent_id is not null",
    Integer.class));
assertEquals(Integer.valueOf(124), legacy.queryForObject(
    "select count(*) from product_feature where product_id=102", Integer.class));
assertEquals(Integer.valueOf(70), legacy.queryForObject(
    "select count(*) from product_document_node n join outline_document_link l "
        + "on l.id=n.outline_link_id where n.product_id=102 and n.node_type='DOCUMENT'",
    Integer.class));
```

- [ ] **Step 2: 验证迁移测试因 V20 不存在失败**

Run: `cd backend && mvn -q -Dtest=SchemaBaselineTest#v20SeparatesConsumerProtectionDocumentsAndCapabilities test`

Expected: FAIL，仍为 26 个错误模块和 70 个错误功能。

- [ ] **Step 3: 实现幂等迁移**

`V20` 按顺序执行：

1. 把旧 26 个模块复制到 `product_document_node(FOLDER)`，通过 `PRODUCT:{productId}:MODULE:{moduleId}` 找到并复用 Outline link。
2. 把旧 70 个功能复制到 `product_document_node(DOCUMENT)`，直接复用 `product_feature.outline_link_id`。
3. 将 `CAP-01` 至 `CAP-10` 的 `parent_id` 置空并排序 10–100。
4. 将旧能力功能 `id 40–70` 的 `code/name/description` 复制为对应 CAP 下的 31 个二级模块。
5. 使用稳定英文编码插入第 5 节定义的 124 个原子功能，每个二级模块四个功能。
6. 只有在 70 个旧功能均已成为有 Outline 映射的文档节点、且三张功能引用表计数均为 0 时，才解除旧功能 Outline 关联并删除旧功能；否则利用唯一临时 guard 表让 Flyway 失败并回滚。
7. 删除 `DOC-01` 至 `DOC-11` 和 `SPRINT-00` 至 `SPRINT-04` 业务模块，保留全部 `outline_document_link`。

- [ ] **Step 4: 验证迁移与全量后端测试**

Run: `cd backend && mvn -q -Dtest=SchemaBaselineTest#v20SeparatesConsumerProtectionDocumentsAndCapabilities test`

Expected: PASS，1 test，0 failures。

Run: `cd backend && mvn -q test`

Expected: PASS，0 failures。

- [ ] **Step 5: 提交迁移**

```bash
git add backend/src/main/resources/db/migration/V20__consumer_protection_product_structure.sql backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java
git commit -m "data: rebuild consumer protection product model"
```

- [ ] **Step 6: 构建并升级本地系统**

Run: `docker compose -p zhilu-delivery-main up -d --build backend frontend`

Expected: backend 和 frontend healthcheck 均为 healthy，Flyway schema version 为 `20`。

- [ ] **Step 7: 执行数据库验收**

Run: `docker exec -e MYSQL_PWD=delivery zhilu-delivery-main-mysql-1 mysql -udelivery -Ddelivery --default-character-set=utf8mb4 --batch -e "select count(*) from product_module where product_id=102 and parent_id is null; select count(*) from product_module where product_id=102 and parent_id is not null; select count(*) from product_feature where product_id=102; select count(*) from product_document_node where product_id=102 and parent_id is null; select count(*) from product_document_node where product_id=102 and node_type='DOCUMENT'; select count(*) from product_document_node n join outline_document_link l on l.id=n.outline_link_id where n.product_id=102 and l.sync_status<>'READY';"`

Expected: 依次为 `10`、`31`、`124`、`11`、`70`、`0`。

- [ ] **Step 8: 执行 API 与界面验收**

登录后访问 `/products/102`：

- “模块与功能”仅显示 10 个一级模块、31 个子模块和原子功能，不出现 `DOC-`、`SPRINT-`。
- “产品文档”显示 11 个一级目录和已有文档；打开“产品一页纸”能读到原 Outline 正文与修订。
- 编辑一个文档只更新 Outline 文档；刷新“模块与功能”后层级和计数不变。

- [ ] **Step 9: 更新计划勾选状态并提交验收记录**

将本文件全部步骤改为 `[x]`，在末尾记录后端测试数、前端测试数、数据库六项计数与页面 URL，然后提交：

```bash
git add docs/superpowers/plans/2026-07-20-product-document-capability-separation.md
git commit -m "docs: record product separation verification"
```
