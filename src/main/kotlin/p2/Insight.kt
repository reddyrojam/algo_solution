package p2

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "INSIGHTS")
data class Insight(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
//    val id: Long? = null,
    val objectUid: String,
    val state: String,
    val positionX: Int,
    val positionY: Int,
    val timestamp: Long,
    val zoneUid: String,
    val sectorUid: String,
    val threatCategory: Int,
    val camUid: String,
)