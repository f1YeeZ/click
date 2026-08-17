import { describe, expect, it } from 'vitest'
import { buildCodeMapIndex, getCallabilityMeta, getLearningMeta } from './codeMapIndex'

describe('buildCodeMapIndex', () => {
  it('connects frontend API calls to controller and service methods', () => {
    const index = buildCodeMapIndex({
      '../../../backend/src/main/java/com/example/MouseController.java': `
        package com.example;
        @RequestMapping("/api/v1/mice")
        public class MouseController {
          private final MouseService mice;
          public MouseController(MouseService mice) { this.mice = mice; }
          @GetMapping("/{id}") public String detail(String id) { return mice.detail(id); }
        }
      `,
      '../../../backend/src/main/java/com/example/MouseService.java': `
        package com.example;
        public class MouseService {
          public String detail(String id) { return id; }
        }
      `,
      '../views/MouseView.vue': `
        <script setup>
        import api from '../api/client'
        const loadMouse = async (id) => api.get(\`/mice/\${id}\`)
        </script>
      `,
      '../api/client.js': 'export default {}',
    })

    const loadMouse = index.nodes.find((node) => node.label === 'loadMouse()')
    const controllerDetail = index.nodes.find((node) => node.endpoint === 'GET /api/v1/mice/{id}')
    const serviceDetail = index.nodes.find((node) => node.label === 'detail()' && node.file.endsWith('MouseService.java'))

    expect(loadMouse).toBeTruthy()
    expect(controllerDetail).toBeTruthy()
    expect(serviceDetail).toBeTruthy()
    expect(controllerDetail.language).toBe('java')
    expect(controllerDetail.sourceCode).toContain('public String detail(String id)')
    expect(controllerDetail.sourceCode).toContain('return mice.detail(id);')
    expect(controllerDetail.sourceStartLine).toBeGreaterThan(0)
    expect(index.edges).toContainEqual(expect.objectContaining({ from: loadMouse.id, to: controllerDetail.id, kind: 'http' }))
    expect(index.edges).toContainEqual(expect.objectContaining({ from: controllerDetail.id, to: serviceDetail.id, kind: 'calls' }))
    expect(index.flows.some((flow) => flow.steps.some((step) => step.node === loadMouse.id))).toBe(true)
  })

  it('includes nested records and class membership relationships', () => {
    const index = buildCodeMapIndex({
      '../../../backend/src/main/java/com/example/MouseDtos.java': `
        package com.example;
        public final class MouseDtos {
          public record MouseView(String id, String name) {}
          public static MouseView from(String id) { return new MouseView(id, "Mouse"); }
        }
      `,
    })

    const container = index.nodes.find((node) => node.label === 'MouseDtos')
    const record = index.nodes.find((node) => node.label === 'MouseView')
    const method = index.nodes.find((node) => node.label === 'from()')

    expect(container).toBeTruthy()
    expect(record?.type).toBe('record')
    expect(method).toBeTruthy()
    expect(index.edges).toContainEqual(expect.objectContaining({ from: container.id, to: record.id, kind: 'contains' }))
    expect(index.edges).toContainEqual(expect.objectContaining({ from: container.id, to: method.id, kind: 'contains' }))
  })
})

describe('getLearningMeta', () => {
  it('marks core business entry points as required reading', () => {
    expect(getLearningMeta({
      label: 'recommend()',
      type: 'method',
      area: 'backend',
      file: 'backend/src/main/java/com/example/service/RecommendationService.java',
      signature: 'public Recommendation recommend()',
    })).toMatchObject({ level: 'core', label: '必须理解', order: '第一阶段' })
  })

  it('includes the current security, public settings, and review-display chains', () => {
    const refreshCookie = getLearningMeta({
      label: 'read()',
      type: 'method',
      area: 'backend',
      file: 'backend/src/main/java/com/example/security/RefreshCookieService.java',
      signature: 'public String read()',
    })
    const publicSettings = getLearningMeta({
      label: 'publicSettings()',
      type: 'method',
      area: 'backend',
      file: 'backend/src/main/java/com/example/service/SystemSettingService.java',
      signature: 'public PublicSettings publicSettings()',
    })
    const publicReviews = getLearningMeta({
      label: 'publicReviews()',
      type: 'method',
      area: 'backend',
      file: 'backend/src/main/java/com/example/service/FeedbackService.java',
      signature: 'public PageResponse publicReviews()',
    })
    const publicConfigStore = getLearningMeta({
      label: 'publicConfig',
      type: 'store',
      area: 'frontend',
      file: 'frontend/src/stores/publicConfig.js',
    })

    expect(refreshCookie).toMatchObject({ level: 'core', label: '必须理解' })
    expect(publicSettings).toMatchObject({ level: 'core', label: '必须理解' })
    expect(publicReviews).toMatchObject({ level: 'core', label: '必须理解' })
    expect(publicConfigStore).toMatchObject({ level: 'core', label: '必须理解' })
  })

  it('keeps persistence support behind the main business flow', () => {
    expect(getLearningMeta({
      label: 'MouseMapper',
      type: 'interface',
      area: 'backend',
      file: 'backend/src/main/java/com/example/mapper/MouseMapper.java',
    })).toMatchObject({ level: 'support', label: '理解后再看', order: '第二阶段' })
  })

  it('allows tests and mechanical accessors to be skipped on a first read', () => {
    const testMeta = getLearningMeta({
      label: 'RecommendationServiceTest',
      type: 'test-class',
      area: 'tests',
      file: 'backend/src/test/java/com/example/RecommendationServiceTest.java',
    })
    const accessorMeta = getLearningMeta({
      label: 'getName()',
      type: 'method',
      area: 'backend',
      file: 'backend/src/main/java/com/example/entity/MouseDevice.java',
    })

    expect(testMeta).toMatchObject({ level: 'skip', label: '初读可跳过' })
    expect(accessorMeta).toMatchObject({ level: 'skip', label: '初读可跳过' })
  })
})

describe('getCallabilityMeta', () => {
  it('distinguishes HTTP entry points from private and public Java methods', () => {
    expect(getCallabilityMeta({
      label: 'detail()', type: 'method', language: 'java', endpoint: 'GET /api/v1/mice/{id}',
      file: 'backend/src/main/java/com/example/MouseController.java', signature: 'public MouseDetail detail(UUID id)',
    })).toMatchObject({ callable: true, kind: 'http', label: '可从前端或客户端调用' })
    expect(getCallabilityMeta({
      label: 'shapeScore()', type: 'method', language: 'java',
      file: 'backend/src/main/java/com/example/RecommendationService.java', signature: 'private double shapeScore(MouseDevice mouse)',
    })).toMatchObject({ callable: true, kind: 'private', label: '仅当前 Java 类内部调用' })
    expect(getCallabilityMeta({
      label: 'recommend()', type: 'method', language: 'java',
      file: 'backend/src/main/java/com/example/RecommendationService.java', signature: 'public Recommendation recommend()',
    })).toMatchObject({ callable: true, kind: 'public', label: '其他 Java 类可调用' })
  })

  it('distinguishes exported, page-local, and structural frontend nodes', () => {
    expect(getCallabilityMeta({
      label: 'errorMessage()', type: 'function', exported: true, file: 'frontend/src/api/client.js',
    })).toMatchObject({ callable: true, kind: 'exported', label: '其他前端模块可调用' })
    expect(getCallabilityMeta({
      label: 'load()', type: 'function', exported: false, file: 'frontend/src/views/MouseDetailView.vue',
    })).toMatchObject({ callable: true, kind: 'page', label: '当前 Vue 页面内部可调用' })
    expect(getCallabilityMeta({
      label: 'MouseService', type: 'class', file: 'backend/src/main/java/com/example/MouseService.java',
    })).toMatchObject({ callable: false, kind: 'structure', label: '不能直接调用' })
  })
})
