package vkr.osago.chat;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vkr.osago.agent.AgentAssignmentService;
import vkr.osago.user.UserEntity;
import vkr.osago.user.UserRepository;
import vkr.osago.user.UserStatus;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository users;
    private final AgentAssignmentService agentAssignmentService;

    public ChatController(
            JdbcTemplate jdbcTemplate,
            UserRepository users,
            AgentAssignmentService agentAssignmentService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.users = users;
        this.agentAssignmentService = agentAssignmentService;
    }

    @GetMapping("/client/chat")
    public ClientChatDto clientChat(@AuthenticationPrincipal UserDetails principal) {
        ensureChatSchema();
        UserEntity client = requireUser(principal, UserStatus.CLIENT);
        Long chatId = ensureClientChat(client.getId());
        Long agentId = getChatAgentId(chatId);
        markIncomingMessagesAsRead(chatId, client.getId());

        ChatPolicyDto policy = loadLatestPolicyContext(client.getId());
        ChatTopicDto topic = loadChatTopic(chatId);
        List<ChatPolicyItemDto> clientPolicies = loadClientPolicies(client.getId());
        List<ChatClaimItemDto> activeClaims = loadActiveClaims(client.getId());
        long unreadFromAgent = countUnread(chatId, client.getId());

        return new ClientChatDto(
                chatId,
                loadChatAgent(chatId, client.getId()),
                policy,
                topic,
                clientPolicies,
                activeClaims,
                unreadFromAgent,
                loadMessages(chatId, client.getId(), agentId)
        );
    }

    @PostMapping("/client/chat/topic")
    public ChatTopicDto clientSetTopic(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody SetChatTopicRequest req
    ) {
        ensureChatSchema();
        UserEntity client = requireUser(principal, UserStatus.CLIENT);
        Long chatId = ensureClientChat(client.getId());

        String topicType = normalizeTopicType(req == null ? null : req.topicType());
        if (topicType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topicType is required");
        }

        Long topicRefId = null;
        String topicLabel;

        switch (topicType) {
            case "POLICY" -> {
                if (req.policyId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "policyId is required");
                }
                var rows = jdbcTemplate.query(
                        """
                        select id, number
                        from insurance.policies
                        where id = ? and user_id = ?
                        limit 1
                        """,
                        (rs, rowNum) -> new RefNumber(rs.getLong("id"), rs.getString("number")),
                        req.policyId(),
                        client.getId()
                );
                if (rows.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found");
                }
                topicRefId = rows.get(0).id();
                topicLabel = "Вопрос по полису " + rows.get(0).number();
            }
            case "CLAIM" -> {
                if (req.claimId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "claimId is required");
                }
                var rows = jdbcTemplate.query(
                        """
                        select id, coalesce(number, ('CLAIM-' || id::text)) as number
                        from insurance.claims
                        where id = ? and user_id = ?
                        limit 1
                        """,
                        (rs, rowNum) -> new RefNumber(rs.getLong("id"), rs.getString("number")),
                        req.claimId(),
                        client.getId()
                );
                if (rows.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
                }
                topicRefId = rows.get(0).id();
                topicLabel = "Вопрос по страховому случаю " + rows.get(0).number();
            }
            case "OTHER" -> {
                String note = normalizeMessage(req.note());
                topicLabel = note == null ? "Иной вопрос" : ("Иной вопрос: " + note);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown topicType");
        }

        jdbcTemplate.update(
                """
                update insurance.chats
                set topic_type = ?,
                    topic_ref_id = ?,
                    topic_label = ?
                where id = ? and client_id = ?
                """,
                topicType,
                topicRefId,
                topicLabel,
                chatId,
                client.getId()
        );

        Long agentId = getChatAgentId(chatId);
        createNotification(
                agentId,
                "NEW_MESSAGE",
                "Обновлена тема чата",
                "Клиент выбрал тему: " + topicLabel,
                "CHAT:" + chatId
        );

        return new ChatTopicDto(topicType, topicRefId, topicLabel);
    }

    @PostMapping("/client/chat/messages")
    public ChatMessageDto clientSendMessage(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody SendMessageRequest req
    ) {
        ensureChatSchema();
        UserEntity client = requireUser(principal, UserStatus.CLIENT);
        String message = normalizeMessage(req == null ? null : req.message());
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }

        Long chatId = ensureClientChat(client.getId());
        Long agentId = getChatAgentId(chatId);

        Long messageId = jdbcTemplate.queryForObject(
                """
                insert into insurance.chat_messages (chat_id, sender_id, message_text, created_at)
                values (?, ?, ?, now())
                returning id
                """,
                Long.class,
                chatId,
                client.getId(),
                message
        );
        if (messageId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send message");
        }

        ChatTopicDto topic = loadChatTopic(chatId);
        String topicTail = (topic == null || topic.label() == null || topic.label().isBlank())
                ? ""
                : (" Тема: " + topic.label());

        createNotification(
                agentId,
                "NEW_MESSAGE",
                "Новое сообщение от клиента",
                "В чате пришло новое сообщение." + topicTail,
                "CHAT:" + chatId
        );

        return new ChatMessageDto(messageId, chatId, client.getId(), message, false, OffsetDateTime.now());
    }

    @GetMapping("/agent/chats")
    public List<AgentChatListItemDto> agentChats(@AuthenticationPrincipal UserDetails principal) {
        ensureChatSchema();
        UserEntity agent = requireUser(principal, UserStatus.AGENT);
        return jdbcTemplate.query(
                """
                select c.id as chat_id,
                       c.client_id,
                       u.first_name,
                       u.last_name,
                       u.middle_name,
                       u.email,
                       lc.contact_phone as client_phone,
                       c.topic_label,
                       lm.message_text as last_message,
                       lm.created_at as last_message_at,
                       (
                           select count(*)
                           from insurance.chat_messages m
                           left join insurance.chat_message_reads r
                             on r.message_id = m.id and r.reader_id = ?
                           where m.chat_id = c.id
                             and m.sender_id <> ?
                             and r.message_id is null
                       ) as unread_count
                from insurance.chats c
                join insurance.users u on u.id = c.client_id
                left join lateral (
                    select m.message_text, m.created_at
                    from insurance.chat_messages m
                    where m.chat_id = c.id
                    order by m.created_at desc, m.id desc
                    limit 1
                ) lm on true
                left join lateral (
                    select cl.contact_phone
                    from insurance.claims cl
                    where cl.user_id = c.client_id
                      and cl.contact_phone is not null
                    order by cl.created_at desc, cl.id desc
                    limit 1
                ) lc on true
                where c.agent_id = ?
                order by unread_count desc, lm.created_at desc nulls last, c.id desc
                """,
                (rs, rowNum) -> new AgentChatListItemDto(
                        rs.getLong("chat_id"),
                        rs.getLong("client_id"),
                        buildFio(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name")),
                        rs.getString("email"),
                        rs.getString("client_phone"),
                        rs.getString("topic_label"),
                        rs.getString("last_message"),
                        rs.getObject("last_message_at", OffsetDateTime.class),
                        rs.getLong("unread_count")
                ),
                agent.getId(),
                agent.getId(),
                agent.getId()
        );
    }

    @GetMapping("/agent/chats/{chatId}")
    public AgentChatDetailsDto agentChatDetails(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long chatId
    ) {
        ensureChatSchema();
        UserEntity agent = requireUser(principal, UserStatus.AGENT);

        var rows = jdbcTemplate.query(
                """
                select c.id as chat_id,
                       c.client_id,
                       u.first_name,
                       u.last_name,
                       u.middle_name,
                       u.email,
                       lc.contact_phone as client_phone
                from insurance.chats c
                join insurance.users u on u.id = c.client_id
                left join lateral (
                    select cl.contact_phone
                    from insurance.claims cl
                    where cl.user_id = c.client_id
                      and cl.contact_phone is not null
                    order by cl.created_at desc, cl.id desc
                    limit 1
                ) lc on true
                where c.id = ? and c.agent_id = ?
                limit 1
                """,
                (rs, rowNum) -> new AgentChatHeaderDto(
                        rs.getLong("chat_id"),
                        rs.getLong("client_id"),
                        buildFio(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name")),
                        rs.getString("email"),
                        rs.getString("client_phone")
                ),
                chatId,
                agent.getId()
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found");
        }

        AgentChatHeaderDto header = rows.get(0);
        markIncomingMessagesAsRead(chatId, agent.getId());

        return new AgentChatDetailsDto(
                header,
                loadLatestPolicyContext(header.clientId()),
                loadChatTopic(chatId),
                loadClientPolicies(header.clientId()),
                loadActiveClaims(header.clientId()),
                countUnread(chatId, agent.getId()),
                loadMessages(chatId, agent.getId(), header.clientId())
        );
    }

    @PostMapping("/agent/chats/{chatId}/messages")
    public ChatMessageDto agentSendMessage(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long chatId,
            @RequestBody SendMessageRequest req
    ) {
        ensureChatSchema();
        UserEntity agent = requireUser(principal, UserStatus.AGENT);
        String message = normalizeMessage(req == null ? null : req.message());
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }

        Long clientId = jdbcTemplate.query(
                """
                select client_id
                from insurance.chats
                where id = ? and agent_id = ?
                limit 1
                """,
                rs -> rs.next() ? rs.getLong("client_id") : null,
                chatId,
                agent.getId()
        );
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found");
        }

        Long messageId = jdbcTemplate.queryForObject(
                """
                insert into insurance.chat_messages (chat_id, sender_id, message_text, created_at)
                values (?, ?, ?, now())
                returning id
                """,
                Long.class,
                chatId,
                agent.getId(),
                message
        );
        if (messageId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send message");
        }

        createNotification(
                clientId,
                "NEW_MESSAGE",
                "Новое сообщение от агента",
                "В чате с агентом пришел ответ.",
                "CHAT:" + chatId
        );

        return new ChatMessageDto(messageId, chatId, agent.getId(), message, false, OffsetDateTime.now());
    }

    private void ensureChatSchema() {
        ensureChatReadTable();
        ensureChatTopicColumns();
    }

    private void ensureChatReadTable() {
        jdbcTemplate.execute(
                """
                create table if not exists insurance.chat_message_reads (
                    message_id bigint not null,
                    reader_id bigint not null,
                    read_at timestamp with time zone not null default now(),
                    primary key (message_id, reader_id),
                    foreign key (message_id) references insurance.chat_messages(id) on delete cascade,
                    foreign key (reader_id) references insurance.users(id) on delete cascade
                )
                """
        );
    }

    private void ensureChatTopicColumns() {
        jdbcTemplate.execute("alter table insurance.chats add column if not exists topic_type varchar(24)");
        jdbcTemplate.execute("alter table insurance.chats add column if not exists topic_ref_id bigint");
        jdbcTemplate.execute("alter table insurance.chats add column if not exists topic_label varchar(255)");
    }

    private Long ensureClientChat(Long clientId) {
        Long agentId = agentAssignmentService.ensureAgentAssignedToUser(clientId);
        if (agentId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No available agents");
        }

        Long existingChatId = jdbcTemplate.query(
                """
                select id
                from insurance.chats
                where client_id = ? and agent_id = ?
                order by id desc
                limit 1
                """,
                rs -> rs.next() ? rs.getLong("id") : null,
                clientId,
                agentId
        );
        if (existingChatId != null) {
            return existingChatId;
        }

        Long chatId = jdbcTemplate.queryForObject(
                """
                insert into insurance.chats (client_id, agent_id, created_at)
                values (?, ?, now())
                returning id
                """,
                Long.class,
                clientId,
                agentId
        );
        if (chatId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create chat");
        }
        return chatId;
    }

    private Long getChatAgentId(Long chatId) {
        Long agentId = jdbcTemplate.query(
                "select agent_id from insurance.chats where id = ? limit 1",
                rs -> rs.next() ? rs.getLong("agent_id") : null,
                chatId
        );
        if (agentId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found");
        }
        return agentId;
    }

    private ChatAgentDto loadChatAgent(Long chatId, Long clientId) {
        var rows = jdbcTemplate.query(
                """
                select u.id,
                       u.first_name,
                       u.last_name,
                       u.middle_name,
                       ap.phone
                from insurance.chats c
                join insurance.users u on u.id = c.agent_id
                left join insurance.agent_profiles ap on ap.user_id = u.id
                where c.id = ? and c.client_id = ?
                limit 1
                """,
                (rs, rowNum) -> new ChatAgentDto(
                        rs.getLong("id"),
                        buildFio(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name")),
                        rs.getString("phone")
                ),
                chatId,
                clientId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not assigned");
        }
        return rows.get(0);
    }

    private ChatTopicDto loadChatTopic(Long chatId) {
        var rows = jdbcTemplate.query(
                """
                select topic_type, topic_ref_id, topic_label
                from insurance.chats
                where id = ?
                limit 1
                """,
                (rs, rowNum) -> new ChatTopicDto(
                        rs.getString("topic_type"),
                        (Long) rs.getObject("topic_ref_id"),
                        rs.getString("topic_label")
                ),
                chatId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void markIncomingMessagesAsRead(Long chatId, Long readerId) {
        jdbcTemplate.update(
                """
                insert into insurance.chat_message_reads (message_id, reader_id, read_at)
                select m.id, ?, now()
                from insurance.chat_messages m
                where m.chat_id = ?
                  and m.sender_id <> ?
                  and not exists (
                    select 1
                    from insurance.chat_message_reads r
                    where r.message_id = m.id and r.reader_id = ?
                  )
                """,
                readerId,
                chatId,
                readerId,
                readerId
        );
    }

    private long countUnread(Long chatId, Long readerId) {
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.chat_messages m
                left join insurance.chat_message_reads r
                  on r.message_id = m.id and r.reader_id = ?
                where m.chat_id = ?
                  and m.sender_id <> ?
                  and r.message_id is null
                """,
                Long.class,
                readerId,
                chatId,
                readerId
        );
        return count == null ? 0 : count;
    }

    private List<ChatMessageDto> loadMessages(Long chatId, Long currentUserId, Long peerUserId) {
        return jdbcTemplate.query(
                """
                select m.id, m.chat_id, m.sender_id, m.message_text, m.created_at,
                       exists (
                           select 1
                           from insurance.chat_message_reads r
                           where r.message_id = m.id
                             and r.reader_id = ?
                       ) as read_by_peer
                from insurance.chat_messages m
                where m.chat_id = ?
                order by m.created_at asc, m.id asc
                """,
                (rs, rowNum) -> new ChatMessageDto(
                        rs.getLong("id"),
                        rs.getLong("chat_id"),
                        rs.getLong("sender_id"),
                        rs.getString("message_text"),
                        rs.getBoolean("read_by_peer"),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                peerUserId,
                chatId
        );
    }

    private ChatPolicyDto loadLatestPolicyContext(Long clientId) {
        var rows = jdbcTemplate.query(
                """
                select p.id as policy_id,
                       p.number as policy_number,
                       p.status::text as policy_status,
                       pa.id as application_id,
                       pa.status as application_status
                from insurance.policies p
                left join insurance.policy_applications pa on pa.issued_policy_id = p.id
                where p.user_id = ?
                order by p.created_at desc, p.id desc
                limit 1
                """,
                (rs, rowNum) -> new ChatPolicyDto(
                        rs.getLong("policy_id"),
                        rs.getString("policy_number"),
                        rs.getString("policy_status"),
                        (Long) rs.getObject("application_id"),
                        rs.getString("application_status")
                ),
                clientId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<ChatPolicyItemDto> loadClientPolicies(Long clientId) {
        return jdbcTemplate.query(
                """
                select id, number, status::text as status
                from insurance.policies
                where user_id = ?
                  and status <> 'CANCELLED'::insurance.policy_status
                order by created_at desc, id desc
                limit 20
                """,
                (rs, rowNum) -> new ChatPolicyItemDto(
                        rs.getLong("id"),
                        rs.getString("number"),
                        rs.getString("status")
                ),
                clientId
        );
    }

    private List<ChatClaimItemDto> loadActiveClaims(Long clientId) {
        return jdbcTemplate.query(
                """
                select id, coalesce(number, ('CLM-' || id::text)) as number, status::text as status
                from insurance.claims
                where user_id = ?
                  and status in (
                    'NEW'::insurance.claim_status,
                    'IN_REVIEW'::insurance.claim_status,
                    'NEED_INFO'::insurance.claim_status,
                    'APPROVED'::insurance.claim_status
                  )
                order by created_at desc, id desc
                limit 20
                """,
                (rs, rowNum) -> new ChatClaimItemDto(
                        rs.getLong("id"),
                        rs.getString("number"),
                        rs.getString("status")
                ),
                clientId
        );
    }

    private void createNotification(Long recipientId, String type, String title, String message, String body) {
        jdbcTemplate.update(
                """
                insert into insurance.notifications
                (recipient_id, type, title, message, body, is_read, created_at)
                values (?, ?, ?, ?, ?, false, now())
                """,
                recipientId,
                type,
                title,
                message,
                body
        );
    }

    private UserEntity requireUser(UserDetails principal, UserStatus expectedStatus) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        UserEntity user = users.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != expectedStatus) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return user;
    }

    private String normalizeMessage(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private String normalizeTopicType(String value) {
        if (value == null) return null;
        String t = value.trim().toUpperCase();
        return t.isEmpty() ? null : t;
    }

    private String buildFio(String lastName, String firstName, String middleName) {
        StringBuilder sb = new StringBuilder();
        if (lastName != null && !lastName.isBlank()) sb.append(lastName.trim());
        if (firstName != null && !firstName.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(firstName.trim());
        }
        if (middleName != null && !middleName.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(middleName.trim());
        }
        return sb.isEmpty() ? "Клиент" : sb.toString();
    }

    public record SendMessageRequest(String message) {
    }

    public record SetChatTopicRequest(
            String topicType,
            Long policyId,
            Long claimId,
            String note
    ) {
    }

    public record ChatMessageDto(
            Long id,
            Long chatId,
            Long senderId,
            String message,
            Boolean readByPeer,
            OffsetDateTime createdAt
    ) {
    }

    public record ChatAgentDto(
            Long id,
            String name,
            String phone
    ) {
    }

    public record ChatPolicyDto(
            Long policyId,
            String policyNumber,
            String policyStatus,
            Long applicationId,
            String applicationStatus
    ) {
    }

    public record ChatTopicDto(
            String topicType,
            Long topicRefId,
            String label
    ) {
    }

    public record ClientChatDto(
            Long chatId,
            ChatAgentDto agent,
            ChatPolicyDto policy,
            ChatTopicDto topic,
            List<ChatPolicyItemDto> clientPolicies,
            List<ChatClaimItemDto> activeClaims,
            long unreadFromAgent,
            List<ChatMessageDto> messages
    ) {
    }

    public record AgentChatListItemDto(
            Long chatId,
            Long clientId,
            String clientName,
            String clientEmail,
            String clientPhone,
            String topicLabel,
            String lastMessage,
            OffsetDateTime lastMessageAt,
            long unreadCount
    ) {
    }

    public record AgentChatHeaderDto(
            Long chatId,
            Long clientId,
            String clientName,
            String clientEmail,
            String clientPhone
    ) {
    }

    public record AgentChatDetailsDto(
            AgentChatHeaderDto header,
            ChatPolicyDto policy,
            ChatTopicDto topic,
            List<ChatPolicyItemDto> clientPolicies,
            List<ChatClaimItemDto> activeClaims,
            long unreadFromClient,
            List<ChatMessageDto> messages
    ) {
    }

    public record ChatPolicyItemDto(
            Long id,
            String number,
            String status
    ) {
    }

    public record ChatClaimItemDto(
            Long id,
            String number,
            String status
    ) {
    }

    private record RefNumber(Long id, String number) {
    }
}
