# Cohort Membership
A library for maintaining and observing changes to membership metadata for a cohort.

## API Reference
```java
NewNamespaceResponse newNamespace(final NewNamespaceRequest request) throws MembershipServerException;

NewCohortTypeResponse newCohortType(final NewCohortTypeRequest request) throws MembershipServerException;

NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipServerException;

NewNodeResponse newNode(final NewNodeRequest request) throws MembershipServerException;

ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipServerException;

JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipServerException;

DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipServerException;

LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipServerException;

DeleteCohortResponse deleteCohort(final DeleteCohortRequest request) throws MembershipServerException;

DeleteCohortTypeResponse deleteCohortType(final DeleteCohortTypeRequest request) throws MembershipServerException;

DeleteNodeResponse deleteNode(final DeleteNodeRequest request) throws MembershipServerException;

PurgeNamespaceResponse purgeNamespace(final PurgeNamespaceRequest request) throws MembershipServerException;

static MembershipService getService(final List<InetSocketAddress> serverAddresses);
```

## Usage Example
```java
logger.info("[step-1] create namespace");
final String namespace = "universe";
final NewNamespaceRequest newNamespaceRequestOne = new NewNamespaceRequest();
newNamespaceRequestOne.setNamespace(namespace);
final NewNamespaceResponse newNamespaceResponseOne = membershipService.newNamespace(newNamespaceRequestOne);
assertEquals("/" + namespace, newNamespaceResponseOne.getPath());
assertTrue(newNamespaceResponseOne.isSuccess());

logger.info("[step-2] create nodeOne");
final NewNodeRequest newNodeRequestOne = new NewNodeRequest();
newNodeRequestOne.setNamespace(namespace);
newNodeRequestOne.setNodeId(UUID.randomUUID().toString());
final String serverHostOne = "localhost";
final int serverPortOne = 8000;
newNodeRequestOne.setAddress(new InetSocketAddress(serverHostOne, serverPortOne));
final NewNodeResponse newNodeResponseOne = membershipService.newNode(newNodeRequestOne);
final Node nodeOne = newNodeResponseOne.getNode();
assertNotNull(nodeOne);
assertEquals("/" + namespace + "/nodes/" + nodeOne.getId(), nodeOne.getPath());

logger.info("[step-3] create nodeTwo");
final NewNodeRequest newNodeRequestTwo = new NewNodeRequest();
newNodeRequestTwo.setNamespace(namespace);
newNodeRequestTwo.setNodeId(UUID.randomUUID().toString());
final String serverHostTwo = "localhost";
final int serverPortTwo = 8001;
newNodeRequestOne.setAddress(new InetSocketAddress(serverHostTwo, serverPortTwo));
final NewNodeResponse newNodeResponseTwo = membershipService.newNode(newNodeRequestTwo);
final Node nodeTwo = newNodeResponseTwo.getNode();
assertNotNull(nodeTwo);
assertEquals("/" + namespace + "/nodes/" + nodeTwo.getId(), nodeTwo.getPath());

logger.info("[step-4] create cohortTypeOne");
final NewCohortTypeRequest newCohortTypeRequestOne = new NewCohortTypeRequest();
newCohortTypeRequestOne.setNamespace(namespace);
newCohortTypeRequestOne.setCohortType(CohortType.ONE);
final NewCohortTypeResponse newCohortTypeResponseOne = membershipService.newCohortType(newCohortTypeRequestOne);
assertTrue(newCohortTypeResponseOne.isSuccess());
assertEquals(CohortType.ONE, newCohortTypeResponseOne.getCohortType());
assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestOne.getCohortType().name(), newCohortTypeResponseOne.getPath());

logger.info("[step-5] create cohortTypeTwo");
final NewCohortTypeRequest newCohortTypeRequestTwo = new NewCohortTypeRequest();
newCohortTypeRequestTwo.setNamespace(namespace);
newCohortTypeRequestTwo.setCohortType(CohortType.TWO);
final NewCohortTypeResponse newCohortTypeResponseTwo = membershipService.newCohortType(newCohortTypeRequestTwo);
assertTrue(newCohortTypeResponseTwo.isSuccess());
assertEquals(CohortType.TWO, newCohortTypeResponseTwo.getCohortType());
assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestTwo.getCohortType().name(), newCohortTypeResponseTwo.getPath());

logger.info("[step-6] create cohortOne");
final NewCohortRequest newCohortRequestOne = new NewCohortRequest();
newCohortRequestOne.setNamespace(namespace);
newCohortRequestOne.setCohortId(UUID.randomUUID().toString());
newCohortRequestOne.setCohortType(CohortType.ONE);
final NewCohortResponse newCohortResponseOne = membershipService.newCohort(newCohortRequestOne);
final Cohort cohortOne = newCohortResponseOne.getCohort();
assertNotNull(cohortOne);
assertEquals("/" + namespace + "/cohorts/" + newCohortRequestOne.getCohortType().name() + "/" + cohortOne.getId(), cohortOne.getPath());

logger.info("[step-7] create cohortTwo");
final NewCohortRequest newCohortRequestTwo = new NewCohortRequest();
newCohortRequestTwo.setNamespace(namespace);
newCohortRequestTwo.setCohortId(UUID.randomUUID().toString());
newCohortRequestTwo.setCohortType(CohortType.TWO);
final NewCohortResponse newCohortResponseTwo = membershipService.newCohort(newCohortRequestTwo);
final Cohort cohortTwo = newCohortResponseTwo.getCohort();
assertNotNull(cohortTwo);
assertEquals("/" + namespace + "/cohorts/" + newCohortRequestTwo.getCohortType().name() + "/" + cohortTwo.getId(), cohortTwo.getPath());

logger.info("[step-8] list cohorts, check for cohortOne and cohortTwo");
final ListCohortsRequest listCohortsRequestOne = new ListCohortsRequest();
listCohortsRequestOne.setNamespace(namespace);
final ListCohortsResponse listCohortsResponseOne = membershipService.listCohorts(listCohortsRequestOne);
assertEquals(2, listCohortsResponseOne.getCohorts().size());
assertTrue(listCohortsResponseOne.getCohorts().contains(cohortOne));
assertTrue(listCohortsResponseOne.getCohorts().contains(cohortTwo));

logger.info("[step-9] memberOne joins cohortOne");
final JoinCohortRequest joinCohortRequestOne = new JoinCohortRequest();
joinCohortRequestOne.setNamespace(namespace);
joinCohortRequestOne.setCohortId(cohortOne.getId());
joinCohortRequestOne.setCohortType(cohortOne.getType());
joinCohortRequestOne.setNodeId(nodeOne.getId());
joinCohortRequestOne.setMemberId(UUID.randomUUID().toString());
final JoinCohortResponse joinCohortResponseOne = membershipService.joinCohort(joinCohortRequestOne);
final Member memberOne = joinCohortResponseOne.getMember();
assertNotNull(memberOne);
assertEquals(cohortOne.getPath() + "/members/" + memberOne.getMemberId(), memberOne.getPath());

logger.info("[step-10] memberTwo joins cohortOne");
final JoinCohortRequest joinCohortRequestTwo = new JoinCohortRequest();
joinCohortRequestTwo.setNamespace(namespace);
joinCohortRequestTwo.setCohortId(cohortOne.getId());
joinCohortRequestTwo.setCohortType(cohortOne.getType());
joinCohortRequestTwo.setNodeId(nodeTwo.getId());
joinCohortRequestTwo.setMemberId(UUID.randomUUID().toString());
final JoinCohortResponse joinCohortResponseTwo = membershipService.joinCohort(joinCohortRequestTwo);
final Member memberTwo = joinCohortResponseTwo.getMember();
assertNotNull(memberTwo);
assertEquals(cohortOne.getPath() + "/members/" + memberTwo.getMemberId(), memberTwo.getPath());

logger.info("[step-11] describe cohortOne, check for memberOne and memberTwo");
final DescribeCohortRequest describeCohortRequestOne = new DescribeCohortRequest();
describeCohortRequestOne.setNamespace(namespace);
describeCohortRequestOne.setCohortId(cohortOne.getId());
describeCohortRequestOne.setCohortType(cohortOne.getType());
final DescribeCohortResponse describeCohortResponseOne = membershipService.describeCohort(describeCohortRequestOne);
assertEquals(cohortOne.getId(), describeCohortResponseOne.getCohortId());
assertEquals(cohortOne.getType(), describeCohortResponseOne.getCohortType());
List<Member> members = describeCohortResponseOne.getMembers();
assertEquals(2, members.size());
final List<String> memberIds = new ArrayList<>();
for (final Member member : members) {
    memberIds.add(member.getMemberId());
}
assertTrue(memberIds.contains(memberOne.getMemberId()));
assertTrue(memberIds.contains(memberTwo.getMemberId()));

logger.info("[step-12] describe cohortTwo, should have no members");
final DescribeCohortRequest describeCohortRequestTwo = new DescribeCohortRequest();
describeCohortRequestTwo.setNamespace(namespace);
describeCohortRequestTwo.setCohortId(cohortTwo.getId());
describeCohortRequestTwo.setCohortType(cohortTwo.getType());
final DescribeCohortResponse describeCohortResponseTwo = membershipService.describeCohort(describeCohortRequestTwo);
assertEquals(cohortTwo.getId(), describeCohortResponseTwo.getCohortId());
assertEquals(cohortTwo.getType(), describeCohortResponseTwo.getCohortType());
members = describeCohortResponseTwo.getMembers();
assertEquals(0, members.size());

logger.info("[step-13] memberTwo leaves cohortOne");
final LeaveCohortRequest leaveCohortRequestOne = new LeaveCohortRequest();
leaveCohortRequestOne.setNamespace(namespace);
leaveCohortRequestOne.setCohortId(cohortOne.getId());
leaveCohortRequestOne.setCohortType(cohortOne.getType());
leaveCohortRequestOne.setMemberId(memberTwo.getMemberId());
final LeaveCohortResponse leaveCohortResponseOne = membershipService.leaveCohort(leaveCohortRequestOne);
assertTrue(leaveCohortResponseOne.isSuccess());

logger.info("[step-14] describe cohortOne, check for presence of memberOne only");
final DescribeCohortRequest describeCohortRequestThree = new DescribeCohortRequest();
describeCohortRequestThree.setNamespace(namespace);
describeCohortRequestThree.setCohortId(cohortOne.getId());
describeCohortRequestThree.setCohortType(cohortOne.getType());
final DescribeCohortResponse describeCohortResponseThree = membershipService.describeCohort(describeCohortRequestThree);
assertEquals(cohortOne.getId(), describeCohortResponseThree.getCohortId());
assertEquals(cohortOne.getType(), describeCohortResponseThree.getCohortType());
members = describeCohortResponseThree.getMembers();
assertEquals(1, members.size());
assertEquals(memberOne.getMemberId(), members.get(0).getMemberId());

logger.info("[step-15] delete cohortTwo");
final DeleteCohortRequest deleteCohortRequestOne = new DeleteCohortRequest();
deleteCohortRequestOne.setNamespace(namespace);
deleteCohortRequestOne.setCohortId(cohortTwo.getId());
deleteCohortRequestOne.setCohortType(cohortTwo.getType());
final DeleteCohortResponse deleteCohortResponseOne = membershipService.deleteCohort(deleteCohortRequestOne);
assertEquals(cohortTwo.getId(), deleteCohortResponseOne.getCohortId());
assertTrue(deleteCohortResponseOne.isSuccess());

logger.info("[step-16] delete cohortOne");
final DeleteCohortRequest deleteCohortRequestTwo = new DeleteCohortRequest();
deleteCohortRequestTwo.setNamespace(namespace);
deleteCohortRequestTwo.setCohortId(cohortOne.getId());
deleteCohortRequestTwo.setCohortType(cohortOne.getType());
final DeleteCohortResponse deleteCohortResponseTwo = membershipService.deleteCohort(deleteCohortRequestTwo);
assertEquals(cohortOne.getId(), deleteCohortResponseTwo.getCohortId());
assertTrue(deleteCohortResponseTwo.isSuccess());

logger.info("[step-17] delete cohortTypeOne");
final DeleteCohortTypeRequest deleteCohortTypeRequestOne = new DeleteCohortTypeRequest();
deleteCohortTypeRequestOne.setNamespace(namespace);
deleteCohortTypeRequestOne.setCohortType(cohortOne.getType());
final DeleteCohortTypeResponse deleteCohortTypeResponseOne = membershipService.deleteCohortType(deleteCohortTypeRequestOne);
assertEquals(cohortOne.getType(), deleteCohortTypeResponseOne.getCohortType());
assertTrue(deleteCohortTypeResponseOne.isSuccess());

logger.info("[step-18] delete nodeOne");
final DeleteNodeRequest deleteNodeRequestOne = new DeleteNodeRequest();
deleteNodeRequestOne.setNamespace(namespace);
deleteNodeRequestOne.setNodeId(nodeOne.getId());
final DeleteNodeResponse deleteNodeResponseOne = membershipService.deleteNode(deleteNodeRequestOne);
assertTrue(deleteNodeResponseOne.isSuccess());

logger.info("[step-19] delete nodeTwo");
final DeleteNodeRequest deleteNodeRequestTwo = new DeleteNodeRequest();
deleteNodeRequestTwo.setNamespace(namespace);
deleteNodeRequestTwo.setNodeId(nodeTwo.getId());
final DeleteNodeResponse deleteNodeResponseTwo = membershipService.deleteNode(deleteNodeRequestTwo);
assertTrue(deleteNodeResponseTwo.isSuccess());

logger.info("[step-20] purge namespace");
final PurgeNamespaceRequest purgeNamespaceRequestOne = new PurgeNamespaceRequest();
purgeNamespaceRequestOne.setNamespace(namespace);
final PurgeNamespaceResponse purgeNamespaceResponseOne = membershipService.purgeNamespace(purgeNamespaceRequestOne);
assertEquals(namespace, purgeNamespaceResponseOne.getNamespace());
assertTrue(purgeNamespaceResponseOne.isSuccess());
```
