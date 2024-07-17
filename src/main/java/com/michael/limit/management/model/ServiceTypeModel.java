package com.michael.limit.management.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedBy;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "apic_t_service_type_details")
public class ServiceTypeModel {

    @Id
    @SequenceGenerator(
            name = "apic_t_service_type_details_sequence",
            sequenceName = "apic_t_service_type_details_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "apic_t_service_type_details_sequence"
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "service_type_id")
    private int serviceTypeId;

    @Column(name = "service_type")
    private String serviceType;

    @Column(name = "created_date")
    private String createdDate;

    @Column(name = "created_time")
    private String createdTime;

    @LastModifiedBy
    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    @Column(name = "last_Modified_date")
    private String lastModifiedDate;

    @Column(name = "last_Modified_Date_Time")
    private String lastModifiedDateTime;

    @Column(name = "hash")
    private String hash;
}
