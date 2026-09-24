import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_announcement_repository.dart';
import '../domain/mobile_announcement_models.dart';
import 'announcement_detail_page.dart';
import 'mobile_announcement_presentation.dart';

class AnnouncementsPage extends StatefulWidget {
  const AnnouncementsPage({required this.announcementGateway, super.key});

  final MobileAnnouncementGateway announcementGateway;

  @override
  State<AnnouncementsPage> createState() => _AnnouncementsPageState();
}

class _AnnouncementsPageState extends State<AnnouncementsPage> {
  static const _pageSize = 20;

  final List<MobileAnnouncement> _announcements = [];
  Object? _error;
  int _page = 0;
  int _totalPages = 0;
  int _totalItems = 0;
  bool _loading = true;
  bool _loadingMore = false;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  Future<void> _reload() async {
    if (mounted) {
      setState(() {
        _loading = true;
        _loadingMore = false;
        _error = null;
      });
    }
    try {
      final result = await widget.announcementGateway.list(size: _pageSize);
      if (!mounted) {
        return;
      }
      setState(() {
        _announcements
          ..clear()
          ..addAll(result.items);
        _page = result.page;
        _totalPages = result.totalPages;
        _totalItems = result.totalItems;
      });
    } catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _loadMore() async {
    if (_loadingMore || _page + 1 >= _totalPages) {
      return;
    }
    setState(() => _loadingMore = true);
    try {
      final result = await widget.announcementGateway.list(
        page: _page + 1,
        size: _pageSize,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _announcements.addAll(result.items);
        _page = result.page;
        _totalPages = result.totalPages;
        _totalItems = result.totalItems;
      });
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'No se pudo cargar más comunicados: ${mobileErrorMessage(error)}',
            ),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _loadingMore = false);
      }
    }
  }

  Future<void> _openAnnouncement(MobileAnnouncement announcement) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => AnnouncementDetailPage(
          announcementId: announcement.id,
          announcementGateway: widget.announcementGateway,
        ),
      ),
    );
    if (mounted) {
      await _reload();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return _AnnouncementsError(
        message: mobileErrorMessage(_error!),
        onRetry: _reload,
      );
    }

    return RefreshIndicator(
      onRefresh: _reload,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          Text(
            'Comunicados',
            style: Theme.of(context).textTheme.headlineSmall
                ?.copyWith(fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Text('$_totalItems comunicado(s) disponibles para su organización.'),
          const SizedBox(height: 8),
          const Text(
            'Abrir un comunicado no confirma su lectura. Cuando sea requerido, usted la confirma explícitamente.',
          ),
          const SizedBox(height: 16),
          if (_announcements.isEmpty)
            const _EmptyAnnouncements()
          else
            for (final announcement in _announcements) ...[
              _AnnouncementCard(
                announcement: announcement,
                onTap: () => _openAnnouncement(announcement),
              ),
              const SizedBox(height: 12),
            ],
          if (_page + 1 < _totalPages) ...[
            const SizedBox(height: 4),
            Center(
              child: OutlinedButton.icon(
                onPressed: _loadingMore ? null : _loadMore,
                icon: _loadingMore
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.expand_more),
                label: Text(
                  _loadingMore ? 'Cargando…' : 'Cargar más comunicados',
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _AnnouncementCard extends StatelessWidget {
  const _AnnouncementCard({required this.announcement, required this.onTap});

  final MobileAnnouncement announcement;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final stateColor = announcementReadColor(context, announcement);
    final stateLabel = announcement.requiresReadConfirmation
        ? 'Requiere confirmación'
        : announcement.readAt != null
        ? 'Leído'
        : 'Informativo';
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        key: Key('announcement-${announcement.id}'),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.campaign_outlined),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      announcement.title,
                      style: Theme.of(context).textTheme.titleMedium
                          ?.copyWith(fontWeight: FontWeight.w700),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                announcement.body,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      'Publicado: ${announcementDateTimeLabel(announcement.createdAt)}',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
                  Chip(
                    backgroundColor: stateColor.withValues(alpha: 0.14),
                    side: BorderSide(color: stateColor),
                    label: Text(stateLabel),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptyAnnouncements extends StatelessWidget {
  const _EmptyAnnouncements();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 48),
      child: Center(
        child: Column(
          children: [
            Icon(Icons.campaign_outlined, size: 42),
            SizedBox(height: 12),
            Text('No tiene comunicados disponibles.'),
          ],
        ),
      ),
    );
  }
}

class _AnnouncementsError extends StatelessWidget {
  const _AnnouncementsError({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_outlined, size: 40),
            const SizedBox(height: 16),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar'),
            ),
          ],
        ),
      ),
    );
  }
}
