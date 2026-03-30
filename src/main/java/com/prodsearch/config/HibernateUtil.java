package com.prodsearch.config;

import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import com.prodsearch.import_data.model.Product;

public class HibernateUtil {
    private static SessionFactory sessionFactory;

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            try {
                StandardServiceRegistry standardRegistry = new StandardServiceRegistryBuilder()
                        .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                        .applySetting("hibernate.connection.url", "jdbc:postgresql://localhost:5432/inform_search")
                        .applySetting("hibernate.connection.username", "postgres")
                        .applySetting("hibernate.connection.password", "152404")
                        .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                        .applySetting("hibernate.show_sql", "true")
                        .applySetting("hibernate.hbm2ddl.auto", "update")
                        .build();

                MetadataSources metadataSources = new MetadataSources(standardRegistry);
                metadataSources.addAnnotatedClass(Product.class);

                Metadata metadata = metadataSources.getMetadataBuilder().build();
                sessionFactory = metadata.getSessionFactoryBuilder().build();

            } catch (Exception e) {
                System.err.println("Initial SessionFactory creation failed: " + e);
                e.printStackTrace();
                throw new ExceptionInInitializerError(e);
            }
        }
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}