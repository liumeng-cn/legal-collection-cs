<template>
  <el-container class="chat-layout">
    <el-aside width="260px" class="chat-aside">
      <div class="aside-header">
        <el-button type="primary" size="small" @click="newChat">新建排查</el-button>
      </div>
      <el-scrollbar class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conversation-item"
          :class="{ active: conv.id === currentId }"
          @click="selectConversation(conv.id)"
        >
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
        </div>
        <div v-if="!conversations.length" class="empty-tip">暂无排查记录</div>
      </el-scrollbar>
    </el-aside>

    <el-main class="chat-main">
      <div ref="messageListRef" class="message-list">
        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="message-row"
          :class="msg.role === 'USER' ? 'right' : 'left'"
        >
          <div class="message-bubble" :class="msg.role === 'USER' ? 'user' : 'assistant'">
            {{ msg.content }}
          </div>
        </div>
        <div v-if="streaming" class="message-row left">
          <div class="message-bubble assistant">
            <div v-if="streamingTools.length" class="tool-trace">
              <el-tag v-for="(tool, i) in streamingTools" :key="i" size="small" type="info" class="tool-tag">
                {{ tool }}
              </el-tag>
            </div>
            {{ streamingText }}<span class="cursor">▌</span>
          </div>
        </div>
      </div>
      <div class="input-area">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          placeholder="描述问题现象：案件号 / 报错信息 / 时间 / 操作路径 / 环境..."
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="streaming" @click="send">排查</el-button>
      </div>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listConversations, listMessages } from '../api/chat'
import { streamDiagnose } from '../api/diagnose'

const conversations = ref([])
const messages = ref([])
const currentId = ref(null)
const input = ref('')
const streaming = ref(false)
const streamingText = ref('')
const streamingTools = ref([])
const messageListRef = ref(null)

async function loadConversations() {
  const { data } = await listConversations()
  conversations.value = data
}

async function selectConversation(id) {
  if (streaming.value) return
  currentId.value = id
  const { data } = await listMessages(id)
  messages.value = data
  scrollToBottom()
}

function newChat() {
  if (streaming.value) return
  currentId.value = null
  messages.value = []
}

async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) return
  input.value = ''
  messages.value.push({ role: 'USER', content: text })
  streaming.value = true
  streamingText.value = ''
  streamingTools.value = []
  scrollToBottom()

  try {
    await streamDiagnose(
      {
        conversationId: currentId.value ? String(currentId.value) : null,
        message: text
      },
      {
        onConversation: (id) => {
          currentId.value = Number(id)
        },
        onDelta: (delta) => {
          streamingText.value += delta
          scrollToBottom()
        },
        onTool: (tool) => {
          streamingTools.value.push(tool)
        }
      }
    )
    if (streamingText.value) {
      messages.value.push({ role: 'ASSISTANT', content: streamingText.value })
    }
    loadConversations()
  } catch (err) {
    ElMessage.error(err.message || '排查失败')
  } finally {
    streaming.value = false
    streamingText.value = ''
    streamingTools.value = []
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

onMounted(() => {
  loadConversations()
})
</script>

<style scoped>
.chat-layout {
  height: calc(100vh - 60px);
}
.chat-aside {
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.aside-header {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}
.conversation-list {
  flex: 1;
}
.conversation-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f0f2f5;
}
.conversation-item:hover {
  background: #f5f7fa;
}
.conversation-item.active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
}
.conv-title {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.empty-tip {
  padding: 24px;
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
}
.chat-main {
  display: flex;
  flex-direction: column;
  padding: 0;
  background: #f5f7fa;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
.message-row {
  display: flex;
  margin-bottom: 16px;
}
.message-row.right {
  justify-content: flex-end;
}
.message-row.left {
  justify-content: flex-start;
}
.message-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.message-bubble.user {
  background: #409eff;
  color: #fff;
}
.message-bubble.assistant {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
}
.tool-trace {
  margin-bottom: 8px;
}
.tool-tag {
  margin-right: 6px;
}
.cursor {
  animation: blink 1s step-start infinite;
}
@keyframes blink {
  50% {
    opacity: 0;
  }
}
.input-area {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}
</style>
