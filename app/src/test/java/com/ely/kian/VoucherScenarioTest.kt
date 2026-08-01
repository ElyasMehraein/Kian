package com.ely.kian

import com.ely.kian.data.local.entities.VoucherUtxo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A->B->C->A Transfer Scenario Test
 */
class VoucherScenarioTest {

    // Simple in-memory representation of what the DAO does
    class MockVoucherDao {
        val utxos = mutableListOf<VoucherUtxo>()

        fun insertUtxo(utxo: VoucherUtxo) {
            utxos.add(utxo)
        }

        fun markSpent(utxoId: String) {
            val utxo = utxos.find { it.utxoId == utxoId }
            if (utxo != null) {
                utxos.remove(utxo)
                utxos.add(utxo.copy(spent = true))
            }
        }

        fun getUnspent(owner: String): List<VoucherUtxo> {
            val allPrevIds = utxos.mapNotNull { it.prevUtxoId }.toSet()
            return utxos.filter { 
                it.owner == owner && !it.spent && !it.isRedeeming && it.utxoId !in allPrevIds 
            }
        }
    }

    @Test
    fun testTransferCircle() {
        val daoA = MockVoucherDao()
        val daoB = MockVoucherDao()
        val daoC = MockVoucherDao()

        val pubA = "pubA"
        val pubB = "pubB"
        val pubC = "pubC"
        val assetRef = "35001:pubA:asset1"

        // 1. A Mints 100
        val utxo1 = VoucherUtxo("u1", assetRef, pubA, pubA, 100, null, 1000, false)
        daoA.insertUtxo(utxo1)
        
        assertEquals(1, daoA.getUnspent(pubA).size)
        assertEquals(100L, daoA.getUnspent(pubA)[0].amount)

        // 2. A transfers 100 to B
        // Producer (A) marks u1 spent and creates u2 for B
        daoA.markSpent("u1")
        val utxo2 = VoucherUtxo("u2", assetRef, pubA, pubB, 100, "u1", 1001, false)
        daoA.insertUtxo(utxo2) // Producer keeps track
        daoB.insertUtxo(utxo2) // B receives it

        assertEquals(0, daoA.getUnspent(pubA).size)
        assertEquals(1, daoB.getUnspent(pubB).size)
        assertEquals(100L, daoB.getUnspent(pubB)[0].amount)

        // 3. B transfers 100 to C
        // B sends request to Producer (A). 
        // A marks u2 spent and creates u3 for C
        daoA.markSpent("u2")
        val utxo3 = VoucherUtxo("u3", assetRef, pubA, pubC, 100, "u2", 1002, false)
        daoA.insertUtxo(utxo3)
        daoB.markSpent("u2") // B marks its own spent when notified or when sending
        daoC.insertUtxo(utxo3) // C receives it

        assertEquals(0, daoB.getUnspent(pubB).size)
        assertEquals(1, daoC.getUnspent(pubC).size)
        assertEquals(100L, daoC.getUnspent(pubC)[0].amount)

        // 4. C transfers 100 back to A
        // C sends request to Producer (A).
        // A marks u3 spent and creates u4 for A
        daoA.markSpent("u3")
        val utxo4 = VoucherUtxo("u4", assetRef, pubA, pubA, 100, "u3", 1003, false)
        daoA.insertUtxo(utxo4)
        daoC.markSpent("u3") // C marks its own spent
        // A already has it

        assertEquals(0, daoC.getUnspent(pubC).size)
        assertEquals(1, daoA.getUnspent(pubA).size)
        assertEquals(100L, daoA.getUnspent(pubA)[0].amount)
        assertEquals("u4", daoA.getUnspent(pubA)[0].utxoId)
        
        println("A->B->C->A transfer successful!")
    }
}
