package p2

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Transient

@Entity
class NotificationEvent(
    var code: String? = null,
    var severity: String? = null,
    var zoneUid: String? = null,
    var shortMessage: String? = null,
    var recommendedAction: String? = null,
    var detailedMessage: String? = null,
    var rule: String? = null,

    @Transient
    var params: String? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}