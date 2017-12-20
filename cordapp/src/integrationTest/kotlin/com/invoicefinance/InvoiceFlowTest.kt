package com.invoicefinance

import com.invoicefinance.flows.GenerateInvoiceFlow
import com.invoicefinance.flows.VerifyInvoiceFlow
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.services.Vault
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.CASH
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.driver.driver
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.sequence
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.time.Instant
import java.util.*
import kotlin.test.assertTrue


class InvoiceFlowTest {

    companion object {
        private val SELLER_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(10)) }
        val SELLER: Party get() = Party(CordaX500Name(organisation = "Seller", locality = "Blackpool", country = "GB"), SELLER_KEY.public)

        val DEBTOR_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(20)) }
        val DEBTOR: Party get() = Party(CordaX500Name(organisation = "Debtor", locality = "London", country = "GB"), DEBTOR_KEY.public)


        val blankIdentifier = UniqueIdentifier(null, UUID(0, 0))
        val expectedInitialState = InvoiceState(PartyAndReference(SELLER, OpaqueBytes("1234".toByteArray())), SELLER, DEBTOR, 100.DOLLARS.CASH.amount, Instant.parse("2018-03-01T00:00:00.000Z"), false, blankIdentifier)
    }

    fun Compare(first: InvoiceState, second: InvoiceState): Boolean {
        return first.issuance.party.nameOrNull() == second.issuance.party.nameOrNull() &&
                first.debtor.nameOrNull() == second.debtor.nameOrNull() &&
                first.owner.nameOrNull() == second.owner.nameOrNull() &&
                first.invoiceAmount == second.invoiceAmount &&
                first.verifiedForPayment == second.verifiedForPayment
    }

    @Test
    fun `test invoice issuance flow`() {
        driver(startNodesInProcess = true, isDebug = true) {
            val sellerUser = User("sellerUser", "testPassword1", permissions = setOf(
                    startFlowPermission<GenerateInvoiceFlow>(),
                    startFlowPermission<VerifyInvoiceFlow>()
            ))
            val debtorUser = User("debtorUser", "testPassword2", permissions = setOf(
                    startFlowPermission<GenerateInvoiceFlow>(),
                    startFlowPermission<VerifyInvoiceFlow>()
            ))

            // This starts three nodes simultaneously with startNode, which returns a future that completes when the node
            // has completed startup. Then these are all resolved with getOrThrow which returns the NodeHandle list.
            val (notaryHandle, sellerHandle, debtorHandle) = listOf(
                    startNode(providedName = DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type))),
                    startNode(providedName = SELLER.name, rpcUsers = listOf(sellerUser)),
                    startNode(providedName = DEBTOR.name, rpcUsers = listOf(debtorUser))
            ).map { it.getOrThrow() }

            val sellerClient = sellerHandle.rpcClientToNode().start("sellerUser", "testPassword1").proxy
            val debtorClient = debtorHandle.rpcClientToNode().start("debtorUser", "testPassword2").proxy
            sellerClient.waitUntilNetworkReady().getOrThrow()
            debtorClient.waitUntilNetworkReady().getOrThrow()

            val sellerVault = sellerClient.vaultTrackBy<InvoiceState>().updates
            val debtorVault = debtorClient.vaultTrackBy<InvoiceState>().updates

            sellerClient.startFlow(::GenerateInvoiceFlow, "1234", 100, Instant.parse("2018-03-01T00:00:00.000Z"), DEBTOR.name.toString()).returnValue.getOrThrow()
            debtorClient.startFlow(::VerifyInvoiceFlow, "1234")

            val expectedEvents =                         listOf(
                    expect { update: Vault.Update<InvoiceState> ->
                        require(update.consumed.size == 0) { "Should not consume any inputs"}
                        require(update.produced.size == 1) { "Should produce one thingy" }
                        require(Compare(update.produced.single().state.data, expectedInitialState)) { "Should have the correct state" }
                    },
                    expect { update: Vault.Update<InvoiceState> ->
                        val newState = expectedInitialState.copy(verifiedForPayment = true)
                        require(update.consumed.size == 1) { "Should consume the previous invoice"}
                        require(update.produced.size == 1) { "Should produce one thingy" }
                        require(Compare(update.produced.single().state.data, newState)) { "Should have the correct state" }
                    }
            )

            debtorVault.expectEvents {
                sequence(expectedEvents)
            }

            sellerVault.expectEvents {
                sequence(expectedEvents)
            }
        }
    }
}