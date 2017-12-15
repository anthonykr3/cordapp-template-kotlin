package com.invoicefinance

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object InvoiceSchema

@CordaSerializable
object InvoiceSchemaV1: MappedSchema(schemaFamily = InvoiceSchema.javaClass, version = 1, mappedTypes = listOf(PersistentInvoiceState::class.java)) {
    @Entity
    @Table(name = "invoice_states")
    class PersistentInvoiceState(
            @Column(name = "issuance_key_hash", length = 130)
            var issuancePartyHash: String,

            @Column(name = "issuance_ref")
            var issuanceRef: ByteArray,

            @Column(name = "owner_key_hash", length = 130)
            var ownerHash: String,

            @Column(name = "debtor_key_hash", length = 130)
            var debtorHash: String,

            @Column(name = "invoice_amount")
            var invoiceAmount: Long,

            @Column(name = "invoice_amount_currency")
            var invoiceAmountCurrency: String,

            @Column(name = "due_on")
            var dueOn: Instant,

            @Column(name = "verified_for_payment")
            var verifiedForPayment: Boolean

    ) : PersistentState()
}



/*

        val issuance: PartyAndReference,
        override val owner: AbstractParty,
        var debtor: AbstractParty,
        var invoiceAmount: Amount<Issued<Currency>>,
        val dueOn: Instant,
        val verifiedForPayment: Boolean

 */