package com.personalrecords.api.sample;

import com.personalrecords.api.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "sample_data_initializations", uniqueConstraints = @UniqueConstraint(
        name = "uk_sample_data_initializations_user_id", columnNames = "user_id"))
public class SampleDataInitialization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected SampleDataInitialization() {
    }

    public SampleDataInitialization(User user) {
        this.user = user;
    }
}
