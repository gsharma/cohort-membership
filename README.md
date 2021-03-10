# Cohort Membership
A library for maintaining and observing changes to membership metadata for a cohort. This library also provides simple primitives for lease-based distributed locking.

## API Reference
```java
NewNamespaceResponse newNamespace(final NewNamespaceRequest request) throws MembershipClientException;

NewCohortTypeResponse newCohortType(final NewCohortTypeRequest request) throws MembershipClientException;

NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipClientException;

NewNodeResponse newNode(final NewNodeRequest request) throws MembershipClientException;

ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipClientException;

JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipClientException;

DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipClientException;

LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipClientException;

DeleteCohortResponse deleteCohort(final DeleteCohortRequest request) throws MembershipClientException;

DeleteCohortTypeResponse deleteCohortType(final DeleteCohortTypeRequest request) throws MembershipClientException;

ListNodesResponse listNodes(final ListNodesRequest request) throws MembershipClientException;

DeleteNodeResponse deleteNode(final DeleteNodeRequest request) throws MembershipClientException;

PurgeNamespaceResponse purgeNamespace(final PurgeNamespaceRequest request) throws MembershipClientException;

AcquireLockResponse acquireLock(final AcquireLockRequest request) throws MembershipClientException;

ReleaseLockResponse releaseLock(final ReleaseLockRequest request) throws MembershipClientException;

static MembershipClient getClient(final String serverHost, final int serverPort, final long serverDeadlineSeconds, final int workerCount);
```

## Usage Example
```java
logger.info("[step-1] create namespace");
final String namespace = "testBasicJoin";
final NewNamespaceRequest newNamespaceRequestOne = NewNamespaceRequest.newBuilder().setNamespace(namespace).build();
final NewNamespaceResponse newNamespaceResponseOne = client.newNamespace(newNamespaceRequestOne);
assertTrue(newNamespaceResponseOne.getSuccess());

logger.info("[step-2] create nodeOne");
final String serverHostOne = "localhost";
final int serverPortOne = 8000;
final NewNodeRequest newNodeRequestOne = NewNodeRequest.newBuilder()
        .setNamespace(namespace)
        .setNodeId(UUID.randomUUID().toString())
        .setPersona(NodePersona.COMPUTE)
        .setAddress(serverHostOne + ":" + serverPortOne).build();
final NewNodeResponse newNodeResponseOne = client.newNode(newNodeRequestOne);
final Node nodeOne = newNodeResponseOne.getNode();
assertNotNull(nodeOne);
assertEquals("/" + namespace + "/nodes/" + nodeOne.getId(), nodeOne.getPath());

logger.info("[step-3] create nodeTwo");
final String serverHostTwo = "localhost";
final int serverPortTwo = 8001;
final NewNodeRequest newNodeRequestTwo = NewNodeRequest.newBuilder()
        .setNamespace(namespace)
        .setNodeId(UUID.randomUUID().toString())
        .setPersona(NodePersona.DATA_COMPUTE)
        .setAddress(serverHostTwo + ":" + serverPortTwo).build();
final NewNodeResponse newNodeResponseTwo = client.newNode(newNodeRequestTwo);
final Node nodeTwo = newNodeResponseTwo.getNode();
assertNotNull(nodeTwo);
assertEquals("/" + namespace + "/nodes/" + nodeTwo.getId(), nodeTwo.getPath());

logger.info("[step-4] create cohortTypeOne");
final NewCohortTypeRequest newCohortTypeRequestOne = NewCohortTypeRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortType(CohortType.ONE).build();
final NewCohortTypeResponse newCohortTypeResponseOne = client.newCohortType(newCohortTypeRequestOne);
assertTrue(newCohortTypeResponseOne.getSuccess());

logger.info("[step-5] create cohortTypeTwo");
final NewCohortTypeRequest newCohortTypeRequestTwo = NewCohortTypeRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortType(CohortType.TWO).build();
final NewCohortTypeResponse newCohortTypeResponseTwo = client.newCohortType(newCohortTypeRequestTwo);
assertTrue(newCohortTypeResponseTwo.getSuccess());

logger.info("[step-6] create cohortOne");
final NewCohortRequest newCohortRequestOne = NewCohortRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortId(UUID.randomUUID().toString())
        .setCohortType(CohortType.ONE).build();
final NewCohortResponse newCohortResponseOne = client.newCohort(newCohortRequestOne);
final Cohort cohortOne = newCohortResponseOne.getCohort();
assertNotNull(cohortOne);
assertEquals("/" + namespace + "/cohorts/" + newCohortRequestOne.getCohortType().name() + "/" + cohortOne.getId(), cohortOne.getPath());

logger.info("[step-7] create cohortTwo");
final NewCohortRequest newCohortRequestTwo = NewCohortRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortId(UUID.randomUUID().toString())
        .setCohortType(CohortType.TWO).build();
final NewCohortResponse newCohortResponseTwo = client.newCohort(newCohortRequestTwo);
final Cohort cohortTwo = newCohortResponseTwo.getCohort();
assertNotNull(cohortTwo);
assertEquals("/" + namespace + "/cohorts/" + newCohortRequestTwo.getCohortType().name() + "/" + cohortTwo.getId(), cohortTwo.getPath());

logger.info("[step-8] list cohorts, check for cohortOne and cohortTwo");
final ListCohortsRequest listCohortsRequestOne = ListCohortsRequest.newBuilder()
        .setNamespace(namespace).build();
final ListCohortsResponse listCohortsResponseOne = client.listCohorts(listCohortsRequestOne);
assertEquals(2, listCohortsResponseOne.getCohortsList().size());
assertTrue(listCohortsResponseOne.getCohortsList().contains(cohortOne));
assertTrue(listCohortsResponseOne.getCohortsList().contains(cohortTwo));

logger.info("[step-9] memberOne joins cohortOne");
final JoinCohortRequest joinCohortRequestOne = JoinCohortRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortId(cohortOne.getId())
        .setCohortType(cohortOne.getType())
        .setNodeId(nodeOne.getId())
        .setMemberId(UUID.randomUUID().toString()).build();
final String memberOneId = joinCohortRequestOne.getMemberId();
final JoinCohortResponse joinCohortResponseOne = client.joinCohort(joinCohortRequestOne);
assertEquals(1, joinCohortResponseOne.getCohort().getMembersList().size());

logger.info("[step-10] memberTwo joins cohortOne");
final JoinCohortRequest joinCohortRequestTwo = JoinCohortRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortId(cohortOne.getId())
        .setCohortType(cohortOne.getType())
        .setNodeId(nodeTwo.getId())
        .setMemberId(UUID.randomUUID().toString()).build();
final String memberTwoId = joinCohortRequestTwo.getMemberId();
final JoinCohortResponse joinCohortResponseTwo = client.joinCohort(joinCohortRequestTwo);
assertEquals(2, joinCohortResponseTwo.getCohort().getMembersList().size());

logger.info("[step-11] describe cohortOne, check for memberOne and memberTwo");
final DescribeCohortRequest describeCohortRequestOne = DescribeCohortRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortId(cohortOne.getId())
        .setCohortType(cohortOne.getType()).build();
final DescribeCohortResponse describeCohortResponseOne = client.describeCohort(describeCohortRequestOne);
final Cohort describedCohortOne = describeCohortResponseOne.getCohort();
assertEquals(cohortOne.getId(), describedCohortOne.getId());
assertEquals(cohortOne.getType(), describedCohortOne.getType());
List<Member> members = describedCohortOne.getMembersList();
assertEquals(2, members.size());
final List<String> memberIds = new ArrayList<>();
for (final Member member : members) {
    memberIds.add(member.getMemberId());
}
assertTrue(memberIds.contains(memberOneId));
assertTrue(memberIds.contains(memberTwoId));

logger.info("[step-12] describe cohortTwo, should have no members");
final DescribeCohortRequest describeCohortRequestTwo = DescribeCohortRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortId(cohortTwo.getId())
        .setCohortType(cohortTwo.getType()).build();
final DescribeCohortResponse describeCohortResponseTwo = client.describeCohort(describeCohortRequestTwo);
final Cohort describedCohortTwo = describeCohortResponseTwo.getCohort();
assertEquals(cohortTwo.getId(), describedCohortTwo.getId());
assertEquals(cohortTwo.getType(), describedCohortTwo.getType());
members = describedCohortTwo.getMembersList();
assertEquals(0, members.size());

logger.info("[step-13] memberTwo leaves cohortOne");
final LeaveCohortRequest leaveCohortRequestOne = LeaveCohortRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortId(cohortOne.getId())
        .setCohortType(cohortOne.getType())
        .setMemberId(memberTwoId).build();
final LeaveCohortResponse leaveCohortResponseOne = client.leaveCohort(leaveCohortRequestOne);
assertTrue(leaveCohortResponseOne.getSuccess());

logger.info("[step-14] describe cohortOne, check for presence of memberOne only");
final DescribeCohortRequest describeCohortRequestThree = DescribeCohortRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortId(cohortOne.getId())
        .setCohortType(cohortOne.getType()).build();
final DescribeCohortResponse describeCohortResponseThree = client.describeCohort(describeCohortRequestThree);
final Cohort describedCohortThree = describeCohortResponseThree.getCohort();
assertEquals(cohortOne.getId(), describedCohortThree.getId());
assertEquals(cohortOne.getType(), describedCohortThree.getType());
members = describedCohortThree.getMembersList();
assertEquals(1, members.size());
assertEquals(memberOneId, members.get(0).getMemberId());

logger.info("[step-15] delete cohortTwo");
final DeleteCohortRequest deleteCohortRequestOne = DeleteCohortRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortId(cohortTwo.getId())
        .setCohortType(cohortTwo.getType()).build();
final DeleteCohortResponse deleteCohortResponseOne = client.deleteCohort(deleteCohortRequestOne);
assertTrue(deleteCohortResponseOne.getSuccess());

logger.info("[step-16] delete cohortOne");
final DeleteCohortRequest deleteCohortRequestTwo = DeleteCohortRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortId(cohortOne.getId())
        .setCohortType(cohortOne.getType()).build();
final DeleteCohortResponse deleteCohortResponseTwo = client.deleteCohort(deleteCohortRequestTwo);
assertTrue(deleteCohortResponseTwo.getSuccess());

logger.info("[step-17] delete cohortTypeOne");
final DeleteCohortTypeRequest deleteCohortTypeRequestOne = DeleteCohortTypeRequest.newBuilder()
        .setNamespace(namespace)
        .setCohortType(cohortOne.getType()).build();
final DeleteCohortTypeResponse deleteCohortTypeResponseOne = client.deleteCohortType(deleteCohortTypeRequestOne);
assertTrue(deleteCohortTypeResponseOne.getSuccess());

logger.info("[step-18] delete nodeOne");
final DeleteNodeRequest deleteNodeRequestOne = DeleteNodeRequest.newBuilder()
        .setNamespace(namespace)
        .setNodeId(nodeOne.getId()).build();
final DeleteNodeResponse deleteNodeResponseOne = client.deleteNode(deleteNodeRequestOne);
assertTrue(deleteNodeResponseOne.getSuccess());

logger.info("[step-19] delete nodeTwo");
final DeleteNodeRequest deleteNodeRequestTwo = DeleteNodeRequest.newBuilder()
        .setNamespace(namespace)
        .setNodeId(nodeTwo.getId()).build();
final DeleteNodeResponse deleteNodeResponseTwo = client.deleteNode(deleteNodeRequestTwo);
assertTrue(deleteNodeResponseTwo.getSuccess());

logger.info("[step-20] purge namespace");
final PurgeNamespaceRequest purgeNamespaceRequestOne = PurgeNamespaceRequest.newBuilder()
        .setNamespace(namespace).build();
final PurgeNamespaceResponse purgeNamespaceResponseOne = client.purgeNamespace(purgeNamespaceRequestOne);
assertTrue(purgeNamespaceResponseOne.getSuccess());
```
