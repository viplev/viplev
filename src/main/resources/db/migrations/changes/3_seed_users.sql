-- All seed passwords are: password

-- Admin users
INSERT INTO users (id, email, username, password, name) VALUES
    (gen_random_uuid(), 'admin1@viplev.dk', 'admin1', '$2a$10$2SThyTLGf2lUlY7VVGqKZuoHd3q0X7RnTizeQGQAaoqPLRgD6FSGi', 'Admin 1'),
    (gen_random_uuid(), 'admin2@viplev.dk', 'admin2', '$2a$10$2SThyTLGf2lUlY7VVGqKZuoHd3q0X7RnTizeQGQAaoqPLRgD6FSGi', 'Admin 2');

-- Regular users
INSERT INTO users (id, email, username, password, name) VALUES
    (gen_random_uuid(), 'user1@viplev.dk', 'user1', '$2a$10$2SThyTLGf2lUlY7VVGqKZuoHd3q0X7RnTizeQGQAaoqPLRgD6FSGi', 'User 1'),
    (gen_random_uuid(), 'user2@viplev.dk', 'user2', '$2a$10$2SThyTLGf2lUlY7VVGqKZuoHd3q0X7RnTizeQGQAaoqPLRgD6FSGi', 'User 2'),
    (gen_random_uuid(), 'user3@viplev.dk', 'user3', '$2a$10$2SThyTLGf2lUlY7VVGqKZuoHd3q0X7RnTizeQGQAaoqPLRgD6FSGi', 'User 3'),
    (gen_random_uuid(), 'user4@viplev.dk', 'user4', '$2a$10$2SThyTLGf2lUlY7VVGqKZuoHd3q0X7RnTizeQGQAaoqPLRgD6FSGi', 'User 4');

-- All users get USER role
INSERT INTO user_roles (user_id, role)
    SELECT id, 'USER' FROM users;

-- Admin users also get ADMIN role
INSERT INTO user_roles (user_id, role)
    SELECT id, 'ADMIN' FROM users WHERE email IN ('admin1@viplev.dk', 'admin2@viplev.dk');
