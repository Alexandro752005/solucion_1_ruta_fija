package pe.rutafija.fleet.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

/** Query mapping only. Membership writes are serialized and audited by the service. */
@Entity
@Table(name="group_driver_membership")
@IdClass(DriverGroupMembership.Key.class)
public class DriverGroupMembership {
    @Id @Column(name="group_id") private UUID groupId;
    @Id @Column(name="driver_id") private UUID driverId;
    @Column(name="organization_id") private UUID organizationId;
    @Column private boolean active;
    protected DriverGroupMembership() { }
    public static class Key implements Serializable {
        @java.io.Serial
        private static final long serialVersionUID = 4920960030694723187L;
        public UUID groupId; public UUID driverId;
        public Key() { }
        @Override public boolean equals(Object other){return other instanceof Key k && java.util.Objects.equals(groupId,k.groupId)&&java.util.Objects.equals(driverId,k.driverId);}
        @Override public int hashCode(){return java.util.Objects.hash(groupId,driverId);}
    }
}
