DELETE FROM user_roles WHERE user_id IN (
    SELECT id FROM users WHERE email IN (
        'admin1@viplev.dk', 'admin2@viplev.dk',
        'user1@viplev.dk', 'user2@viplev.dk',
        'user3@viplev.dk', 'user4@viplev.dk'
    )
);

DELETE FROM users WHERE email IN (
    'admin1@viplev.dk', 'admin2@viplev.dk',
    'user1@viplev.dk', 'user2@viplev.dk',
    'user3@viplev.dk', 'user4@viplev.dk'
);
